import io
import logging
import os
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Optional
from urllib.parse import unquote, urlparse

import requests
from tenacity import retry, retry_if_exception_type, stop_after_attempt, wait_exponential

from config import settings
from shared.debug_artifacts import save_debug_bytes


logger = logging.getLogger(__name__)


class MinerUError(RuntimeError):
    pass


class MinerUClient:
    def __init__(self) -> None:
        self.session = requests.Session()
        self.session.headers.update(
            {
                "Authorization": f"Bearer {settings.mineru_token}",
                "Content-Type": "application/json",
            }
        )

    # 提取文件并返回Markdown文本
    def extract_markdown(
        self,
        file_url: str,
        task_id: str,
        file_type: str = "",
        *,
        debug_dir: Optional[Path] = None,
    ) -> str:
        if self._is_remote_url(file_url):
            logger.info("Submitting remote file to MinerU task_id=%s", task_id)
            return self._extract_remote(file_url, task_id, debug_dir=debug_dir)

        path = self._resolve_local_path(file_url)
        if not path.is_absolute() or not path.exists():
            raise MinerUError(f"fileUrl must be a remote URL or existing absolute path: {file_url}")

        logger.info("Uploading local file to MinerU task_id=%s path=%s type=%s", task_id, path, file_type)
        return self._extract_local(path, task_id, debug_dir=debug_dir)

    @staticmethod
    def _resolve_local_path(file_url: str) -> Path:
        if file_url.startswith("file://"):
            # 移除 file:// 前缀
            raw_path = file_url[7:]

            # Windows 平台下，如果是 file:///D:/xxx 这种，raw_path 就是 /D:/xxx
            if len(raw_path) >= 3 and raw_path[0] == "/" and raw_path[2] == ":":
                raw_path = raw_path[1:]

            # 直接使用 unquote 解码 URL 编码（以防止像 %20 这样的字符）
            path_str = unquote(raw_path)
            return Path(path_str)
        return Path(file_url)

    # 提取文件并返回原始文本
    def _extract_remote(self, url: str, task_id: str, *, debug_dir: Optional[Path] = None) -> str:
        payload = self._remote_payload(task_id)
        payload["url"] = url
        response = self._post_json("/extract/task", payload)
        mineru_task_id = response["data"]["task_id"]
        result = self._poll_single_task(mineru_task_id)
        return self._download_full_md(result["full_zip_url"], task_id, debug_dir=debug_dir)

    # 提取本地文件需要先上传到MinerU，获取批次ID后轮询批次结果，最后下载Markdown文本
    def _extract_local(self, path: Path, task_id: str, *, debug_dir: Optional[Path] = None) -> str:
        payload = self._batch_payload()
        payload["files"] = [{"name": path.name, "data_id": task_id, "is_ocr": settings.mineru_is_ocr}]

        response = self._post_json("/file-urls/batch", payload)
        data = response["data"]
        batch_id = data["batch_id"]
        upload_urls = data.get("file_urls") or []
        if not upload_urls:
            raise MinerUError("MinerU did not return an upload URL")

        self._upload_file(upload_urls[0], path)
        result = self._poll_batch(batch_id, path.name, task_id)
        return self._download_full_md(result["full_zip_url"], task_id, debug_dir=debug_dir)

    # 构造远程文件提取的请求负载
    def _remote_payload(self, task_id: str) -> Dict[str, Any]:
        payload = self._batch_payload()
        payload.update({"data_id": task_id, "is_ocr": settings.mineru_is_ocr})
        return payload

    # 构造批量提取的请求负载
    @staticmethod
    def _batch_payload() -> Dict[str, Any]:
        return {
            "model_version": settings.mineru_model_version,
            "enable_formula": settings.mineru_enable_formula,
            "enable_table": settings.mineru_enable_table,
            "language": settings.mineru_language,
        }

    # 带重试机制的POST请求，处理网络异常和API错误
    @retry(
        retry=retry_if_exception_type((requests.RequestException, MinerUError)),
        wait=wait_exponential(multiplier=1, min=2, max=30),
        stop=stop_after_attempt(3),
        reraise=True,
    )
    def _post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        response = self.session.post(
            f"{settings.mineru_base_url}{path}",
            json=payload,
            timeout=settings.mineru_http_timeout_seconds,
        )
        response.raise_for_status()
        data = response.json()
        if data.get("code") != 0:
            raise MinerUError(f"MinerU API error code={data.get('code')} msg={data.get('msg')}")
        return data

    # 带重试机制的GET请求，处理网络异常和API错误
    @retry(
        retry=retry_if_exception_type(requests.RequestException),
        wait=wait_exponential(multiplier=1, min=2, max=30),
        stop=stop_after_attempt(3),
        reraise=True,
    )
    def _get_json(self, path: str) -> Dict[str, Any]:
        response = self.session.get(
            f"{settings.mineru_base_url}{path}",
            timeout=settings.mineru_http_timeout_seconds,
        )
        response.raise_for_status()
        data = response.json()
        if data.get("code") != 0:
            raise MinerUError(f"MinerU API error code={data.get('code')} msg={data.get('msg')}")
        return data

    # 带重试机制的文件上传，处理网络异常和API错误
    @retry(
        retry=retry_if_exception_type(requests.RequestException),
        wait=wait_exponential(multiplier=1, min=2, max=30),
        stop=stop_after_attempt(3),
        reraise=True,
    )
    def _upload_file(self, upload_url: str, path: Path) -> None:
        with path.open("rb") as file_obj:
            response = requests.put(
                upload_url,
                data=file_obj,
                headers={},
                timeout=settings.mineru_http_timeout_seconds,
            )
        response.raise_for_status()
        if response.status_code != 200:
            raise MinerUError(f"MinerU upload failed status={response.status_code}")

    # 轮询单个提取任务的状态，直到完成或失败，返回结果数据
    def _poll_single_task(self, mineru_task_id: str) -> Dict[str, Any]:
        deadline = time.monotonic() + settings.mineru_poll_timeout_seconds
        while time.monotonic() < deadline:
            data = self._get_json(f"/extract/task/{mineru_task_id}")["data"]
            state = data.get("state")
            if state == "done":
                if not data.get("full_zip_url"):
                    raise MinerUError("MinerU task completed without full_zip_url")
                return data
            if state == "failed":
                raise MinerUError(f"MinerU task failed: {data.get('err_msg') or 'unknown error'}")
            logger.info("MinerU task pending mineru_task_id=%s state=%s", mineru_task_id, state)
            time.sleep(settings.mineru_poll_interval_seconds)
        raise MinerUError(f"MinerU task timed out mineru_task_id={mineru_task_id}")

    # 轮询批量提取的结果，匹配文件名或任务ID，直到完成或失败，返回结果数据
    def _poll_batch(self, batch_id: str, file_name: str, task_id: str) -> Dict[str, Any]:
        deadline = time.monotonic() + settings.mineru_poll_timeout_seconds
        while time.monotonic() < deadline:
            data = self._get_json(f"/extract-results/batch/{batch_id}")["data"]
            results = data.get("extract_result") or []
            matched = self._match_batch_result(results, file_name, task_id)
            if matched:
                state = matched.get("state")
                if state == "done":
                    if not matched.get("full_zip_url"):
                        raise MinerUError("MinerU batch item completed without full_zip_url")
                    return matched
                if state == "failed":
                    raise MinerUError(f"MinerU batch item failed: {matched.get('err_msg') or 'unknown error'}")
                logger.info("MinerU batch item pending batch_id=%s state=%s", batch_id, state)
            else:
                logger.info("MinerU batch result not visible yet batch_id=%s file_name=%s", batch_id, file_name)
            time.sleep(settings.mineru_poll_interval_seconds)
        raise MinerUError(f"MinerU batch timed out batch_id={batch_id}")

    # 在批量结果中匹配对应的文件名或任务ID，返回对应的结果项
    @staticmethod
    def _match_batch_result(results: list[Dict[str, Any]], file_name: str, task_id: str) -> Dict[str, Any] | None:
        for item in results:
            if item.get("data_id") == task_id or item.get("file_name") == file_name:
                return item
        return None

    # 带重试机制的下载和解压，处理网络异常、API错误和ZIP文件错误
    @retry(
        retry=retry_if_exception_type((requests.RequestException, MinerUError, zipfile.BadZipFile)),
        wait=wait_exponential(multiplier=1, min=2, max=30),
        stop=stop_after_attempt(3),
        reraise=True,
    )
    def _download_full_md(
        self,
        zip_url: str,
        task_id: str,
        *,
        debug_dir: Optional[Path] = None,
    ) -> str:
        response = requests.get(zip_url, timeout=settings.mineru_http_timeout_seconds)
        response.raise_for_status()
        if settings.debug_mode:
            if debug_dir is not None:
                save_debug_bytes(
                    debug_dir / "mineru.zip",
                    response.content,
                    label="MinerU zip",
                    task_id=task_id,
                )
            else:
                self._save_debug_zip(response.content, task_id)
        with zipfile.ZipFile(io.BytesIO(response.content)) as zip_file:
            md_names = [name for name in zip_file.namelist() if os.path.basename(name) in {"full.md", "auto.md"}]
            if not md_names:
                raise MinerUError("返回压缩包中未找到full.md或auto.md文件")
            name = md_names[0]
            info = zip_file.getinfo(name)
            if info.filename.startswith("/") or ".." in Path(info.filename).parts:
                raise MinerUError(f"非法 ZIP 路径: {info.filename}")
            with zip_file.open(info) as md_file:
                return md_file.read().decode("utf-8", errors="replace")

    @staticmethod
    def _save_debug_zip(content: bytes, task_id: str) -> Path:
        temp_dir = Path(settings.debug_temp_dir)
        temp_dir.mkdir(parents=True, exist_ok=True)
        safe_task_id = "".join(c if c.isalnum() or c in "-_" else "_" for c in task_id) or "unknown"
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        zip_path = temp_dir / f"{safe_task_id}_{timestamp}.zip"
        zip_path.write_bytes(content)
        logger.info("Debug mode: saved MinerU zip task_id=%s path=%s size=%s", task_id, zip_path, len(content))
        return zip_path

    @staticmethod
    def _is_remote_url(value: str) -> bool:
        parsed = urlparse(value)
        return parsed.scheme in {"http", "https"}
