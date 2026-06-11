"""将 LLM 输出规范为 Java QuestionPreviewVO，并做与 AiImportStreamConsumer 一致的校验。"""
from __future__ import annotations

import re
from typing import Any, Dict, List, Optional

_LETTER = re.compile(r"^[A-Z]$")
_VALID_TYPES = frozenset({"SINGLE", "MULTI", "JUDGE", "SHORT_ANSWER"})


def normalize_text_answers(raw: List[Any]) -> List[str]:
    out: List[str] = []
    for item in raw or []:
        if item is None:
            continue
        text = str(item).strip()
        if text:
            out.append(text)
    return out


def normalize_answers(raw: List[Any]) -> List[str]:
    out: List[str] = []
    for item in raw or []:
        if item is None:
            continue
        t = str(item).strip().upper()
        if not t:
            continue
        if _LETTER.match(t):
            out.append(t)
        elif t in ("T", "F"):
            out.append(t)
    if not out:
        return []
    if len(out) > 1 and all(_LETTER.match(x) for x in out):
        out = sorted(dict.fromkeys(out))
    return out


def letters_in_range(option_count: int, answers: List[str]) -> bool:
    for letter in answers:
        if not _LETTER.match(letter):
            return False
        idx = ord(letter) - ord("A")
        if idx < 0 or idx >= option_count:
            return False
    return True


def to_preview_vo(dto: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    if not isinstance(dto, dict):
        return None

    qtype = dto.get("questionType")
    if qtype is None or str(qtype).strip() not in _VALID_TYPES:
        return None
    qtype = str(qtype).strip()

    stem = dto.get("stem")
    if stem is None or not str(stem).strip():
        return None
    stem = str(stem).strip()

    raw_answer = dto.get("answer")
    if not isinstance(raw_answer, list) or not raw_answer:
        return None

    options = list(dto.get("options") or [])
    if qtype == "SHORT_ANSWER":
        options = []
        answers = normalize_text_answers(raw_answer)
        if not answers:
            return None
    elif qtype == "JUDGE":
        answers = normalize_answers(raw_answer)
        if not answers:
            return None
        options = ["正确", "错误"]
        if len(answers) != 1 or answers[0] not in ("T", "F"):
            return None
    else:
        answers = normalize_answers(raw_answer)
        if not answers:
            return None
        if not options or any(o is None or not str(o).strip() for o in options):
            return None
        options = [str(o) for o in options]
        if not letters_in_range(len(options), answers):
            return None

    analysis = dto.get("analysis")
    if analysis is None:
        analysis = ""
    else:
        analysis = str(analysis)

    return {
        "questionType": qtype,
        "stem": stem,
        "options": options,
        "answer": answers,
        "analysis": analysis,
    }


def build_preview_list(parsed: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    previews: List[Dict[str, Any]] = []
    for item in parsed:
        vo = to_preview_vo(item)
        if vo is not None:
            previews.append(vo)
    return previews
