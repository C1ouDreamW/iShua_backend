"""Debug 模式下将 MinerU / LLM 中间产物写入 DEBUG_TEMP_DIR。"""
from __future__ import annotations

import logging
from datetime import datetime, timezone
from pathlib import Path

from config import settings


logger = logging.getLogger(__name__)


def sanitize_task_id(task_id: str) -> str:
    safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in task_id)
    return safe or "unknown"


def create_task_debug_dir(task_id: str) -> Path:
    """为单次任务创建目录：{DEBUG_TEMP_DIR}/{taskId}_{UTC时间戳}/"""
    base = Path(settings.debug_temp_dir)
    base.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    task_dir = base / f"{sanitize_task_id(task_id)}_{timestamp}"
    task_dir.mkdir(parents=True, exist_ok=True)
    logger.info("Debug mode: task artifact dir task_id=%s path=%s", task_id, task_dir)
    return task_dir


def save_debug_text(path: Path, content: str, *, label: str, task_id: str) -> None:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        logger.info(
            "Debug mode: saved %s task_id=%s path=%s chars=%s",
            label,
            task_id,
            path,
            len(content),
        )
    except OSError as exc:
        logger.warning(
            "Debug mode: failed to save %s task_id=%s path=%s error=%s",
            label,
            task_id,
            path,
            exc,
        )


def save_debug_bytes(path: Path, content: bytes, *, label: str, task_id: str) -> None:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        logger.info(
            "Debug mode: saved %s task_id=%s path=%s size=%s",
            label,
            task_id,
            path,
            len(content),
        )
    except OSError as exc:
        logger.warning(
            "Debug mode: failed to save %s task_id=%s path=%s error=%s",
            label,
            task_id,
            path,
            exc,
        )
