"""阶段 2 AI 解答核心算法：分片 + 多次投票。

仅处理 SINGLE/MULTI/JUDGE 三类客观题；SHORT_ANSWER 不进入此流程。

置信度规则：
- SINGLE/JUDGE：3/3 一致 → HIGH；2/3 一致 → MEDIUM；无多数 → LOW（取众数，标存疑）
- MULTI：全部字母 3/3 出现 → HIGH；部分字母 2/3 出现 → MEDIUM；无字母达 2/3 → LOW
"""
from __future__ import annotations

import json
import logging
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from openai import OpenAI
from tenacity import retry, stop_after_attempt, wait_exponential

from config import settings


logger = logging.getLogger(__name__)

_PROMPTS_DIR = Path(__file__).resolve().parent / "prompts"
_DEFAULT_SYSTEM_PROMPT_FILE = _PROMPTS_DIR / "ai-answer-system.txt"

_LETTER = re.compile(r"^[A-Z]$")
_SUPPORTED_TYPES = frozenset({"SINGLE", "MULTI", "JUDGE"})


def _resolve_system_prompt_path() -> Path:
    configured = settings.answer_system_prompt_path
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
            logger.info("Loaded answer system prompt from %s", path)
            return text
        raise ValueError(f"Answer system prompt file is empty: {path}")
    raise FileNotFoundError(
        f"ANSWER_SYSTEM_PROMPT_PATH does not exist or is not a file: {path}"
    )


def _build_user_prompt(shard: List[Dict[str, Any]]) -> str:
    """构造用户提示词：仅含 questionType/stem/options，不泄露答案。"""
    items = []
    for i, q in enumerate(shard):
        items.append({
            "index": i,
            "questionType": q.get("questionType"),
            "stem": q.get("stem"),
            "options": q.get("options") or [],
        })
    return "请解答以下客观题，严格按系统提示词输出 JSON 数组。\n\n题目：\n" + json.dumps(
        items, ensure_ascii=False, indent=2
    )


def _parse_json_array(content: str) -> List[Dict[str, Any]]:
    cleaned = content.strip()
    fence_match = re.search(r"```(?:json)?\s*(.*?)\s*```", cleaned, flags=re.IGNORECASE | re.DOTALL)
    if fence_match:
        cleaned = fence_match.group(1).strip()
    elif not cleaned.startswith("["):
        array_match = re.search(r"\[[\s\S]*\]", cleaned)
        if array_match:
            cleaned = array_match.group(0).strip()
    cleaned = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", "", cleaned)
    data = json.loads(cleaned)
    if not isinstance(data, list):
        raise ValueError("LLM result must be a JSON array")
    return data


def _normalize_answer_letters(raw: Any) -> List[str]:
    out: List[str] = []
    for item in raw or []:
        if item is None:
            continue
        t = str(item).strip().upper()
        if not t:
            continue
        if _LETTER.match(t) or t in ("T", "F"):
            out.append(t)
    if len(out) > 1 and all(_LETTER.match(x) for x in out):
        out = sorted(dict.fromkeys(out))
    return out


def _vote_single_or_judge(candidates: List[Optional[List[str]]]) -> Tuple[List[str], str]:
    """SINGLE/JUDGE 投票：candidates 为各轮答案（每轮 0 或 1 个字母/T/F）。"""
    valid = [c[0] for c in candidates if c]
    if not valid:
        return [], "LOW"

    counts: Dict[str, int] = {}
    for a in valid:
        counts[a] = counts.get(a, 0) + 1
    sorted_answers = sorted(counts.items(), key=lambda x: (-x[1], x[0]))
    winner, winner_count = sorted_answers[0]
    rounds = len(candidates)

    if winner_count >= rounds:
        confidence = "HIGH"
    elif winner_count >= rounds - 1 and rounds >= 2:
        confidence = "MEDIUM"
    else:
        confidence = "LOW"
    return [winner], confidence


def _vote_multi(candidates: List[Optional[List[str]]]) -> Tuple[List[str], str]:
    """MULTI 投票：按字母统计出现次数。"""
    rounds = len(candidates)
    letter_counts: Dict[str, int] = {}
    for c in candidates:
        if not c:
            continue
        for letter in set(c):
            letter_counts[letter] = letter_counts.get(letter, 0) + 1

    if not letter_counts:
        return [], "LOW"

    high_letters = [l for l, n in letter_counts.items() if n >= rounds]
    medium_letters = [l for l, n in letter_counts.items() if n >= rounds - 1 and n < rounds]

    if high_letters and len(high_letters) == len(letter_counts):
        return sorted(high_letters), "HIGH"
    if high_letters or medium_letters:
        chosen = set(high_letters) | set(medium_letters)
        return sorted(chosen), "MEDIUM"
    # 无字母达 2/3：取出现 ≥2 次的（若 rounds≥3），否则取众数字母
    threshold = 2 if rounds >= 3 else 1
    fallback = [l for l, n in letter_counts.items() if n >= threshold]
    if not fallback:
        fallback = [max(letter_counts.items(), key=lambda x: x[1])[0]]
    return sorted(fallback), "LOW"


def _vote(question: Dict[str, Any], candidates: List[Dict[str, Any]]) -> Dict[str, Any]:
    """对单题投票，返回带答案与置信度的 QuestionPreviewVO dict。"""
    q_type = question.get("questionType")
    candidate_answers: List[Optional[List[str]]] = []
    candidate_analyses: List[str] = []
    for c in candidates:
        if c is None:
            candidate_answers.append(None)
            candidate_analyses.append("")
            continue
        ans = c.get("answer")
        candidate_answers.append(_normalize_answer_letters(ans) if ans is not None else None)
        brief = c.get("analysisBrief")
        candidate_analyses.append(str(brief) if brief is not None else "")

    if q_type in ("SINGLE", "JUDGE"):
        answer, confidence = _vote_single_or_judge(candidate_answers)
    else:  # MULTI
        answer, confidence = _vote_multi(candidate_answers)

    original_analysis = str(question.get("analysis") or "")
    brief = candidate_analyses[0] if candidate_analyses and candidate_analyses[0] else ""

    if confidence == "LOW":
        analysis = ("【AI解答·存疑】" + (brief or original_analysis)).strip()
    else:
        analysis = (brief or original_analysis).strip()

    if not answer:
        # 投票完全失败，标 MISSING 与失败标记
        return {
            "questionType": q_type,
            "stem": question.get("stem"),
            "options": question.get("options") or [],
            "answer": [],
            "analysis": "【AI解答·失败】投票未产生有效答案",
            "answerSource": "MISSING",
            "answerConfidence": "LOW",
        }

    return {
        "questionType": q_type,
        "stem": question.get("stem"),
        "options": question.get("options") or [],
        "answer": answer,
        "analysis": analysis,
        "answerSource": "AI_GENERATED",
        "answerConfidence": confidence,
    }


def _failed_question(question: Dict[str, Any], reason: str) -> Dict[str, Any]:
    """分片失败时，该片中题目以 MISSING + 失败标记返回。"""
    return {
        "questionType": question.get("questionType"),
        "stem": question.get("stem"),
        "options": question.get("options") or [],
        "answer": [],
        "analysis": "【AI解答·失败】" + reason,
        "answerSource": "MISSING",
        "answerConfidence": "LOW",
    }


class AnswerGenerator:
    """分片 + 投票解答生成器。"""

    def __init__(self) -> None:
        self.system_prompt = _load_system_prompt()
        model = settings.answer_llm_model or settings.llm_model
        self.client = OpenAI(
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            timeout=settings.answer_llm_timeout_seconds,
        )
        self.model = model
        self.shard_size = settings.answer_shard_size
        self.vote_rounds = settings.answer_vote_rounds
        self.temperature = settings.answer_temperature
        self.max_concurrency = settings.answer_max_concurrency

    def generate(
        self,
        questions: List[Dict[str, Any]],
    ) -> Tuple[List[Dict[str, Any]], int, int]:
        """返回 (结果列表, LLM 总耗时 ms, 总调用次数)。

        结果列表顺序与输入一致；分片失败不阻塞整批。
        """
        if not questions:
            return [], 0, 0

        # 仅保留支持的客观题；其余原样返回为 MISSING
        targets: List[Dict[str, Any]] = []
        skipped: List[Dict[str, Any]] = []
        for q in questions:
            if q.get("questionType") in _SUPPORTED_TYPES:
                targets.append(q)
            else:
                skipped.append(_failed_question(q, "题型不支持 AI 解答"))

        if not targets:
            return skipped, 0, 0

        shards = [targets[i:i + self.shard_size] for i in range(0, len(targets), self.shard_size)]
        total_calls = 0
        total_ms = 0
        results_by_index: Dict[int, Dict[str, Any]] = {}

        with ThreadPoolExecutor(max_workers=self.max_concurrency) as executor:
            futures = {
                executor.submit(self._answer_shard, shard, shard_start): shard_start
                for shard_start, shard in enumerate(shards)
            }
            for future in as_completed(futures):
                shard_start = futures[future]
                try:
                    shard_results, shard_ms, shard_calls = future.result()
                except Exception as exc:
                    logger.exception("Shard failed shard_start=%s", shard_start)
                    shard = shards[shard_start]
                    shard_results = [_failed_question(q, str(exc)) for q in shard]
                    shard_ms, shard_calls = 0, 0
                total_ms += shard_ms
                total_calls += shard_calls
                for i, r in enumerate(shard_results):
                    results_by_index[shard_start * self.shard_size + i] = r

        ordered = [results_by_index[i] for i in range(len(targets)) if i in results_by_index]
        return ordered + skipped, total_ms, total_calls

    def _answer_shard(
        self,
        shard: List[Dict[str, Any]],
        shard_start: int,
    ) -> Tuple[List[Dict[str, Any]], int, int]:
        """解答一个分片：VOTE_ROUNDS 轮调用 + 逐题投票。"""
        candidates_per_round: List[Optional[List[Dict[str, Any]]]] = []
        total_ms = 0
        total_calls = 0

        for round_idx in range(self.vote_rounds):
            try:
                started = time.monotonic()
                raw = self._call_llm(shard)
                total_ms += int((time.monotonic() - started) * 1000)
                total_calls += 1
                parsed = self._parse_and_align(raw, len(shard))
                candidates_per_round.append(parsed)
            except Exception:
                total_calls += 1
                logger.exception(
                    "LLM call failed shard_start=%s round=%s",
                    shard_start,
                    round_idx,
                )
                candidates_per_round.append(None)

        results: List[Dict[str, Any]] = []
        for i, q in enumerate(shard):
            candidates = [
                round_results[i] if round_results and i < len(round_results) else None
                for round_results in candidates_per_round
            ]
            results.append(_vote(q, candidates))
        return results, total_ms, total_calls

    @retry(wait=wait_exponential(multiplier=1, min=2, max=20), stop=stop_after_attempt(2), reraise=True)
    def _call_llm(self, shard: List[Dict[str, Any]]) -> str:
        user_prompt = _build_user_prompt(shard)
        response = self.client.chat.completions.create(
            model=self.model,
            temperature=self.temperature,
            messages=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        )
        content = response.choices[0].message.content
        if not content:
            raise ValueError("LLM returned empty content")
        return content

    @staticmethod
    def _parse_and_align(content: str, expected_len: int) -> List[Optional[Dict[str, Any]]]:
        """解析 LLM 返回并对齐到 expected_len（缺失位置填 None）。"""
        parsed = _parse_json_array(content)
        aligned: List[Optional[Dict[str, Any]]] = [None] * expected_len
        for item in parsed:
            if not isinstance(item, dict):
                continue
            idx = item.get("index")
            if not isinstance(idx, int) or idx < 0 or idx >= expected_len:
                continue
            aligned[idx] = item
        return aligned
