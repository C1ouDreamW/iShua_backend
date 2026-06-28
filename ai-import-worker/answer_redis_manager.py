"""阶段 2 AI 解答任务专用 Redis 管理（独立 Stream / 消费组 / Key 前缀）。

与 redis_manager.py 物理隔离，互不影响阶段 1 抽题流程。
"""
from __future__ import annotations

import json
import logging
import threading
from contextlib import contextmanager
from typing import Any, Dict, Iterator, List, Optional, Tuple

import redis
from redis.exceptions import ResponseError

from config import settings


logger = logging.getLogger(__name__)

StreamMessage = Tuple[str, Dict[str, Any]]

# 与 Java IShuaRedisCacheConstants 保持一致（默认值；实际使用 settings 中的配置）
STATUS_KEY_PREFIX = "ishua:answer:status:"
RESULT_KEY_PREFIX = "ishua:answer:result:"
MAX_MESSAGE_CHARS = 500

# 终态与已解答态：IMPORTED/FAILED（终态）或 ANSWERED/PARTIAL（已出结果）后，Worker 不应再覆盖
NON_OVERWRITABLE_STATUSES = frozenset({"IMPORTED", "FAILED", "ANSWERED", "PARTIAL"})

# 仅当当前 status 不是终态/已解答态时才写入（与 Java 端 CAS 互斥）
_CAS_NON_TERMINAL_WRITE = """
local cur = redis.call('GET', KEYS[1])
if cur then
    local ok, decoded = pcall(cjson.decode, cur)
    if ok and decoded and decoded.status then
        if decoded.status == 'IMPORTED' or decoded.status == 'FAILED' or decoded.status == 'ANSWERED' or decoded.status == 'PARTIAL' then
            return 0
        end
    end
end
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
return 1
"""


class AnswerRedisManager:
    """解答任务 Redis 客户端：消费 ishua:answer:stream，写状态与结果。"""

    def __init__(self) -> None:
        block_seconds = max(settings.answer_redis_block_ms, 0) / 1000.0
        socket_timeout = None if block_seconds <= 0 else block_seconds + 5.0
        self.client = redis.Redis.from_url(
            settings.redis_url,
            decode_responses=True,
            socket_timeout=socket_timeout,
        )
        self._cas_script = self.client.register_script(_CAS_NON_TERMINAL_WRITE)

    def ensure_group(self) -> None:
        try:
            self.client.xgroup_create(
                name=settings.answer_redis_stream,
                groupname=settings.answer_redis_group,
                id="0",
                mkstream=True,
            )
            logger.info("Created answer consumer group %s", settings.answer_redis_group)
        except ResponseError as exc:
            if "BUSYGROUP" in str(exc):
                logger.info("Answer consumer group %s already exists", settings.answer_redis_group)
                return
            raise

    def read_task(self) -> Optional[StreamMessage]:
        response = self.client.xreadgroup(
            groupname=settings.answer_redis_group,
            consumername=settings.answer_redis_consumer,
            streams={settings.answer_redis_stream: ">"},
            count=1,
            block=settings.answer_redis_block_ms,
        )
        if not response:
            return None

        _, messages = response[0]
        if not messages:
            return None

        message_id, fields = messages[0]
        return message_id, self._normalize_message(fields)

    def set_processing(self, answer_task_id: str, total_count: int) -> None:
        vo = self._status_vo(answer_task_id, "PROCESSING", total_count=total_count, answered_count=0)
        self._write_status_unless_terminal(answer_task_id, vo)

    def set_answered(
        self,
        answer_task_id: str,
        questions: List[Dict[str, Any]],
        total_count: int,
        metrics: Optional[Dict[str, int]] = None,
    ) -> None:
        """全部成功：status=ANSWERED。"""
        self._write_result_and_status(
            answer_task_id=answer_task_id,
            questions=questions,
            status="ANSWERED",
            message=None,
            total_count=total_count,
            answered_count=len(questions),
            metrics=metrics,
        )

    def set_partial(
        self,
        answer_task_id: str,
        questions: List[Dict[str, Any]],
        total_count: int,
        message: Optional[str],
        metrics: Optional[Dict[str, int]] = None,
    ) -> None:
        """部分失败：status=PARTIAL，已成功部分可入库。"""
        self._write_result_and_status(
            answer_task_id=answer_task_id,
            questions=questions,
            status="PARTIAL",
            message=message,
            total_count=total_count,
            answered_count=len(questions),
            metrics=metrics,
        )

    def set_failed(
        self,
        answer_task_id: str,
        reason: str = "解答任务处理失败",
        metrics: Optional[Dict[str, int]] = None,
    ) -> None:
        vo = self._status_vo(
            answer_task_id,
            "FAILED",
            message=self._truncate(reason),
            metrics=metrics,
        )
        self._write_status_unless_terminal(answer_task_id, vo)

    def _write_result_and_status(
        self,
        answer_task_id: str,
        questions: List[Dict[str, Any]],
        status: str,
        message: Optional[str],
        total_count: int,
        answered_count: int,
        metrics: Optional[Dict[str, int]] = None,
    ) -> None:
        result_json = json.dumps(questions, ensure_ascii=False, separators=(",", ":"))
        # 结果数据无终态保护（IMPORTED 时 Java 端会 delete 掉，写入也无害）
        self.client.set(
            self._result_key(answer_task_id),
            result_json,
            ex=settings.answer_result_ttl_seconds,
        )
        status_vo = self._status_vo(
            answer_task_id,
            status,
            message=message,
            total_count=total_count,
            answered_count=answered_count,
            metrics=metrics,
        )
        written = self._write_status_unless_terminal(answer_task_id, status_vo)
        if not written:
            logger.warning(
                "Skip %s write because status already terminal answer_task_id=%s",
                status,
                answer_task_id,
            )

    def _write_status_unless_terminal(self, answer_task_id: str, vo: Dict[str, Any]) -> bool:
        payload = json.dumps(vo, ensure_ascii=False)
        try:
            result = self._cas_script(
                keys=[self._status_key(answer_task_id)],
                args=[payload, settings.answer_status_ttl_seconds],
            )
            return result == 1
        except redis.RedisError:
            logger.exception("CAS write failed, fallback to direct SET answer_task_id=%s", answer_task_id)
            self.client.set(
                self._status_key(answer_task_id),
                payload,
                ex=settings.answer_status_ttl_seconds,
            )
            return True

    def ack(self, message_id: str) -> int:
        return self.client.xack(settings.answer_redis_stream, settings.answer_redis_group, message_id)

    @contextmanager
    def heartbeat(self, answer_task_id: str) -> Iterator[None]:
        stop_event = threading.Event()
        thread = threading.Thread(
            target=self._heartbeat_loop,
            args=(answer_task_id, stop_event),
            name=f"answer-heartbeat-{answer_task_id}",
            daemon=True,
        )
        thread.start()
        try:
            yield
        finally:
            stop_event.set()
            thread.join(timeout=5)

    def _heartbeat_loop(self, answer_task_id: str, stop_event: threading.Event) -> None:
        while not stop_event.wait(settings.answer_heartbeat_interval_seconds):
            try:
                raw = self.client.get(self._status_key(answer_task_id))
                if raw and self._status_from_json(raw) == "PROCESSING":
                    self.client.expire(
                        self._status_key(answer_task_id),
                        settings.answer_status_ttl_seconds,
                    )
                logger.debug("Renewed answer heartbeat answer_task_id=%s", answer_task_id)
            except Exception:
                logger.exception("Failed to renew answer heartbeat answer_task_id=%s", answer_task_id)

    @staticmethod
    def _status_vo(
        answer_task_id: str,
        status: str,
        message: Optional[str] = None,
        total_count: Optional[int] = None,
        answered_count: Optional[int] = None,
        metrics: Optional[Dict[str, int]] = None,
    ) -> Dict[str, Any]:
        vo: Dict[str, Any] = {
            "answerTaskId": answer_task_id,
            "status": status,
            "message": message,
            "totalCount": total_count,
            "answeredCount": answered_count,
            "questions": None,
        }
        if metrics:
            vo["metrics"] = metrics
        return vo

    @staticmethod
    def _truncate(message: str) -> str:
        t = (message or "").strip()
        if len(t) <= MAX_MESSAGE_CHARS:
            return t
        return t[:MAX_MESSAGE_CHARS] + "..."

    @staticmethod
    def _status_from_json(raw: str) -> Optional[str]:
        try:
            data = json.loads(raw)
            if isinstance(data, dict):
                return str(data.get("status") or "")
        except json.JSONDecodeError:
            return raw if raw in {"SUBMITTED", "PROCESSING", "ANSWERED", "PARTIAL", "FAILED", "IMPORTED"} else None
        return None

    @staticmethod
    def _normalize_message(fields: Dict[str, Any]) -> Dict[str, Any]:
        payload = fields.get("payload")
        if isinstance(payload, str):
            try:
                decoded = json.loads(payload)
                if isinstance(decoded, dict):
                    return decoded
            except json.JSONDecodeError:
                pass

        if len(fields) == 1:
            only_value = next(iter(fields.values()))
            if isinstance(only_value, str):
                try:
                    decoded = json.loads(only_value)
                    if isinstance(decoded, dict):
                        return decoded
                except json.JSONDecodeError:
                    pass
        return fields

    @staticmethod
    def _status_key(answer_task_id: str) -> str:
        return f"{STATUS_KEY_PREFIX}{answer_task_id}"

    @staticmethod
    def _result_key(answer_task_id: str) -> str:
        return f"{RESULT_KEY_PREFIX}{answer_task_id}"
