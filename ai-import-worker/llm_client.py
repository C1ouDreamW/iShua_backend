import json
import logging
import re
from pathlib import Path
from typing import Any, Dict, List, Optional

from openai import OpenAI
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings
from debug_artifacts import save_debug_text
from question_preview import build_preview_list


logger = logging.getLogger(__name__)

_PROMPTS_DIR = Path(__file__).resolve().parent / "prompts"
_DEFAULT_SYSTEM_PROMPT_FILE = _PROMPTS_DIR / "ai-import-system.txt"

_FALLBACK_SYSTEM_PROMPT = """你是「非结构化文本 → 结构化题库」解析引擎。
【输出格式】只输出合法 JSON 数组，禁止 Markdown 代码块与说明文字。
每个元素必须且只能包含：questionType、stem、options、answer、analysis。
questionType 只能是 SINGLE、MULTI、JUDGE、SHORT_ANSWER。
answer 必须为非空字符串数组；判断题 options 固定为 ["正确","错误"]，answer 为 ["T"] 或 ["F"]。
无法识别的题块静默丢弃；整段非题库则输出 []。
"""

USER_PROMPT_TEMPLATE = """请从以下 Markdown 内容中抽取结构化试题，严格按系统提示词输出 JSON 数组。

Markdown 内容：
{markdown}
"""


def _resolve_system_prompt_path() -> Path:
    configured = settings.llm_system_prompt_path
    if configured:
        path = Path(configured)
        if not path.is_absolute():
            path = Path(__file__).resolve().parent / path
        return path.resolve()
    return _DEFAULT_SYSTEM_PROMPT_FILE.resolve()


def _load_system_prompt() -> str:
    path = _resolve_system_prompt_path()
    if path.is_file():
        text = path.read_text(encoding="utf-8").strip()
        if text:
            logger.info("Loaded system prompt from %s", path)
            return text
        raise ValueError(f"System prompt file is empty: {path}")

    if settings.llm_system_prompt_path:
        raise FileNotFoundError(
            f"LLM_SYSTEM_PROMPT_PATH does not exist or is not a file: {path}"
        )

    logger.warning(
        "Default system prompt file missing (%s), using built-in fallback",
        path,
    )
    return _FALLBACK_SYSTEM_PROMPT


SYSTEM_PROMPT = _load_system_prompt()


class LLMClient:
    def __init__(self) -> None:
        self.client = OpenAI(
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            timeout=settings.llm_timeout_seconds,
        )

    @retry(wait=wait_exponential(multiplier=1, min=2, max=20), stop=stop_after_attempt(3), reraise=True)
    def extract_questions(
        self,
        markdown: str,
        *,
        task_id: str = "",
        debug_dir: Optional[Path] = None,
    ) -> List[Dict[str, Any]]:
        user_prompt = USER_PROMPT_TEMPLATE.format(markdown=markdown)
        if settings.debug_mode and debug_dir is not None:
            save_debug_text(
                debug_dir / "llm_user_prompt.txt",
                user_prompt,
                label="LLM user prompt",
                task_id=task_id,
            )
        content = self._call_model(user_prompt)
        if settings.debug_mode and debug_dir is not None:
            save_debug_text(
                debug_dir / "llm_raw.txt",
                content,
                label="LLM raw response",
                task_id=task_id,
            )
        parsed = self._parse_json(content)
        self._validate_llm_shape(parsed)
        previews = build_preview_list(parsed)
        logger.info("LLM parsed %s items, %s valid previews", len(parsed), len(previews))
        return previews

    def _call_model(self, user_prompt: str) -> str:
        response = self.client.chat.completions.create(
            model=settings.llm_model,
            temperature=settings.llm_temperature,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
        )
        content = response.choices[0].message.content
        if not content:
            raise ValueError("LLM returned empty content")
        return content

    @staticmethod
    def _parse_json(content: str) -> List[Dict[str, Any]]:
        cleaned = content.strip()
        fence_match = re.search(r"```(?:json)?\s*(.*?)\s*```", cleaned, flags=re.IGNORECASE | re.DOTALL)
        if fence_match:
            cleaned = fence_match.group(1).strip()
        elif not cleaned.startswith("["):
            array_match = re.search(r"\[[\s\S]*\]", cleaned)
            if array_match:
                cleaned = array_match.group(0).strip()

        data = json.loads(cleaned)
        if not isinstance(data, list):
            raise ValueError("LLM result must be a JSON array")
        return data

    @staticmethod
    def _validate_llm_shape(value: List[Dict[str, Any]]) -> None:
        required_keys = {"questionType", "stem", "options", "answer", "analysis"}
        for index, item in enumerate(value):
            if not isinstance(item, dict):
                raise ValueError(f"Question at index {index} must be an object")
            missing = required_keys - item.keys()
            if missing:
                raise ValueError(f"Question at index {index} missing keys: {sorted(missing)}")
            if not isinstance(item["stem"], str):
                raise ValueError(f"stem at index {index} must be a string")
            qtype = str(item["questionType"]).strip()
            if qtype not in {"SINGLE", "MULTI", "JUDGE", "SHORT_ANSWER"}:
                raise ValueError(
                    f"questionType at index {index} must be SINGLE, MULTI, JUDGE, or SHORT_ANSWER"
                )
            if qtype == "SHORT_ANSWER" and item["options"]:
                raise ValueError(f"options at index {index} must be [] for SHORT_ANSWER")
            if not isinstance(item["options"], list):
                raise ValueError(f"options at index {index} must be an array")
            if not isinstance(item["answer"], list) or not item["answer"]:
                raise ValueError(f"answer at index {index} must be a non-empty array")
            if not isinstance(item["analysis"], str):
                raise ValueError(f"analysis at index {index} must be a string")
