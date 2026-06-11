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

# 与 Java IShuaRedisCacheConstants 保持一致
STATUS_KEY_PREFIX = "ishua:task:status:"
RESULT_KEY_PREFIX = "ishua:task:result:"
STATUS_TTL_SECONDS = 3600
RESULT_TTL_SECONDS = 1800
MAX_MESSAGE_CHARS = 500

# 终态：Watchdog 已强制 FAILED 或 Java 已落库 IMPORTED 时，Worker 不应再覆盖
TERMINAL_STATUSES = frozenset({"IMPORTED", "FAILED"})

# 「仅当当前 status 不属于终态集合时，才写入新值」的 Lua 脚本（与 Java 端 CAS 互斥）
_CAS_NON_TERMINAL_WRITE = """
local cur = redis.call('GET', KEYS[1])
if cur then
    local ok, decoded = pcall(cjson.decode, cur)
    if ok and decoded and decoded.status then
        if decoded.status == 'IMPORTED' or decoded.status == 'FAILED' then
            return 0
        end
    end
end
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
return 1
"""


class RedisManager:
    def __init__(self) -> None:
        # redis-py 8 默认 socket_timeout=5s；XREADGROUP block 需更长读超时，否则会误抛 TimeoutError
        block_seconds = max(settings.redis_block_ms, 0) / 1000.0
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
                name=settings.redis_stream,
                groupname=settings.redis_group,
                id="0",
                mkstream=True,
            )
            logger.info("Created Redis consumer group %s", settings.redis_group)
        except ResponseError as exc:
            if "BUSYGROUP" in str(exc):
                logger.info("Redis consumer group %s already exists", settings.redis_group)
                return
            raise

    def read_task(self) -> Optional[StreamMessage]:
        response = self.client.xreadgroup(
            groupname=settings.redis_group,
            consumername=settings.redis_consumer,
            streams={settings.redis_stream: ">"},
            count=1,
            block=settings.redis_block_ms,
        )
        if not response:
            return None

        _, messages = response[0]
        if not messages:
            return None

        message_id, fields = messages[0]
        return message_id, self._normalize_message(fields)

    def set_processing(self, task_id: str) -> None:
        vo = self._status_vo(task_id, "PROCESSING")
        self._write_status_unless_terminal(task_id, vo)

    def set_parsed(
        self,
        task_id: str,
        previews: List[Dict[str, Any]],
        metrics: Optional[Dict[str, int]] = None,
    ) -> None:
        result_json = json.dumps(previews, ensure_ascii=False, separators=(",", ":"))
        message = None
        if not previews:
            message = "解析完成但无可落库题目"
        status_vo = self._status_vo(
            task_id,
            "PARSED",
            message=message,
            total_count=len(previews),
            metrics=metrics,
        )
        # 结果数据无终态保护（IMPORTED 时 Java 端会 delete 掉，PARSED 写入也无害）
        self.client.set(self._result_key(task_id), result_json, ex=RESULT_TTL_SECONDS)
        # 状态走终态保护：若已被 Watchdog 标 FAILED 或被 Java 落库 IMPORTED，则跳过覆盖
        written = self._write_status_unless_terminal(task_id, status_vo)
        if not written:
            logger.warning(
                "Skip PARSED write because status already terminal task_id=%s",
                task_id,
            )

    def set_failed(
        self,
        task_id: str,
        reason: str = "任务处理失败",
        metrics: Optional[Dict[str, int]] = None,
    ) -> None:
        vo = self._status_vo(task_id, "FAILED", message=self._truncate(reason), metrics=metrics)
        # FAILED 也走终态保护：若 Java 已 IMPORTED 终态，Worker 不应回退；若已是 FAILED 则等价于幂等
        self._write_status_unless_terminal(task_id, vo)

    def _write_status_unless_terminal(self, task_id: str, vo: Dict[str, Any]) -> bool:
        """
        仅当当前 status 不是 IMPORTED/FAILED 时才写入。
        返回 True 表示写入成功；False 表示因终态保护被跳过。
        """
        payload = json.dumps(vo, ensure_ascii=False)
        try:
            result = self._cas_script(
                keys=[self._status_key(task_id)],
                args=[payload, STATUS_TTL_SECONDS],
            )
            return result == 1
        except redis.RedisError:
            logger.exception("CAS write failed, fallback to direct SET task_id=%s", task_id)
            self.client.set(self._status_key(task_id), payload, ex=STATUS_TTL_SECONDS)
            return True

    def ack(self, message_id: str) -> int:
        return self.client.xack(settings.redis_stream, settings.redis_group, message_id)

    @contextmanager
    def heartbeat(self, task_id: str) -> Iterator[None]:
        stop_event = threading.Event()
        thread = threading.Thread(
            target=self._heartbeat_loop,
            args=(task_id, stop_event),
            name=f"task-heartbeat-{task_id}",
            daemon=True,
        )
        thread.start()
        try:
            yield
        finally:
            stop_event.set()
            thread.join(timeout=5)

    def _heartbeat_loop(self, task_id: str, stop_event: threading.Event) -> None:
        while not stop_event.wait(settings.heartbeat_interval_seconds):
            try:
                raw = self.client.get(self._status_key(task_id))
                if raw and self._status_from_json(raw) == "PROCESSING":
                    self.client.expire(self._status_key(task_id), STATUS_TTL_SECONDS)
                logger.debug("Renewed task heartbeat task_id=%s", task_id)
            except Exception:
                logger.exception("Failed to renew task heartbeat task_id=%s", task_id)

    @staticmethod
    def _status_vo(
        task_id: str,
        status: str,
        message: Optional[str] = None,
        total_count: Optional[int] = None,
        metrics: Optional[Dict[str, int]] = None,
    ) -> Dict[str, Any]:
        vo: Dict[str, Any] = {
            "taskId": task_id,
            "status": status,
            "message": message,
            "totalCount": total_count,
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
            return raw if raw in {"PROCESSING", "PARSED", "FAILED", "SUBMITTED"} else None
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
    def _status_key(task_id: str) -> str:
        return f"{STATUS_KEY_PREFIX}{task_id}"

    @staticmethod
    def _result_key(task_id: str) -> str:
        return f"{RESULT_KEY_PREFIX}{task_id}"
