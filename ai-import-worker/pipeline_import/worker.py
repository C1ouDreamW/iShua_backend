import logging
import signal
import sys
import time
from typing import Any, Dict

from config import settings
from shared.debug_artifacts import create_task_debug_dir, save_debug_text
from pipeline_import.llm_client import LLMClient
from pipeline_import.mineru_client import MinerUClient
from pipeline_import.redis_manager import RedisManager


logger = logging.getLogger(__name__)
shutdown_requested = False


def configure_logging() -> None:
    logging.basicConfig(
        level=getattr(logging, settings.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


def request_shutdown(signum: int, _frame: Any) -> None:
    global shutdown_requested
    shutdown_requested = True
    logger.info("Received signal %s, worker will stop after current iteration", signum)


def require_task_field(task: Dict[str, Any], field: str) -> str:
    value = task.get(field)
    if value is None or str(value).strip() == "":
        raise ValueError(f"Task message missing required field: {field}")
    return str(value)


def first_present(task: Dict[str, Any], *fields: str) -> str:
    for field in fields:
        value = task.get(field)
        if value is not None and str(value).strip() != "":
            return str(value)
    return ""


def build_pipeline_metrics(mineru_ms: int | None, llm_ms: int | None) -> Dict[str, int] | None:
    if mineru_ms is None and llm_ms is None:
        return None
    mineru = mineru_ms or 0
    llm = llm_ms or 0
    return {"mineruMs": mineru, "llmMs": llm, "pipelineMs": mineru + llm}


def infer_file_type(*candidates: str) -> str:
    for candidate in candidates:
        if not candidate:
            continue
        name = candidate.split("?", 1)[0]
        if "." in name:
            ext = name.rsplit(".", 1)[-1].strip().lower()
            if ext:
                return ext
    return ""


def process_task(
    redis_manager: RedisManager,
    mineru_client: MinerUClient,
    llm_client: LLMClient,
    message_id: str,
    task: Dict[str, Any],
) -> None:
    task_id = str(task.get("taskId") or "")
    mineru_ms: int | None = None
    llm_ms: int | None = None

    try:
        task_id = require_task_field(task, "taskId")
        task_kind = (first_present(task, "type", "taskType") or "file").strip().lower()

        logger.info("Accepted task message_id=%s task_id=%s task_kind=%s", message_id, task_id, task_kind)
        redis_manager.set_processing(task_id)

        with redis_manager.heartbeat(task_id):
            if task_kind != "file":
                raise ValueError(
                    f"Unsupported task type: {task_kind}; only 'file' is supported by 流程 A"
                )

            file_url = require_task_field(task, "fileUrl")
            file_name = first_present(task, "fileName")
            file_type = first_present(task, "fileType", "fileExt", "extension", "mimeType")
            if not file_type:
                file_type = infer_file_type(file_name, file_url)
            debug_dir = create_task_debug_dir(task_id) if settings.debug_mode else None

            mineru_started = time.monotonic()
            markdown = mineru_client.extract_markdown(
                file_url=file_url,
                task_id=task_id,
                file_type=file_type,
                debug_dir=debug_dir,
            )
            mineru_ms = int((time.monotonic() - mineru_started) * 1000)
            if settings.debug_mode and debug_dir is not None:
                save_debug_text(
                    debug_dir / "markdown.md",
                    markdown,
                    label="MinerU markdown",
                    task_id=task_id,
                )

            if settings.skip_llm:
                logger.info("SKIP_LLM enabled, skipping LLM extraction task_id=%s", task_id)
                questions = []
                llm_ms = 0
            else:
                llm_started = time.monotonic()
                questions = llm_client.extract_questions(
                    markdown,
                    task_id=task_id,
                    debug_dir=debug_dir,
                )
                llm_ms = int((time.monotonic() - llm_started) * 1000)
            metrics = build_pipeline_metrics(mineru_ms, llm_ms)
            redis_manager.set_parsed(task_id, questions, metrics=metrics)
        redis_manager.ack(message_id)
        logger.info("Task parsed and ACKed task_id=%s message_id=%s questions=%s", task_id, message_id, len(questions))
    except Exception as exc:
        logger.exception("Task failed task_id=%s message_id=%s", task_id, message_id)
        if task_id:
            try:
                reason = str(exc).strip() or exc.__class__.__name__
                redis_manager.set_failed(task_id, reason, metrics=build_pipeline_metrics(mineru_ms, llm_ms))
            except Exception:
                logger.exception("Failed to update FAILED status task_id=%s", task_id)
        try:
            redis_manager.ack(message_id)
            logger.info("Failed task ACKed task_id=%s message_id=%s", task_id, message_id)
        except Exception:
            logger.exception("Failed to ACK failed task task_id=%s message_id=%s", task_id, message_id)


def main() -> int:
    configure_logging()
    signal.signal(signal.SIGINT, request_shutdown)
    signal.signal(signal.SIGTERM, request_shutdown)

    settings.validate()
    if settings.debug_mode:
        logger.info("DEBUG_MODE enabled, artifacts dir=%s", settings.debug_temp_dir)
    if settings.skip_llm:
        logger.warning("SKIP_LLM enabled, LLM extraction is disabled")

    redis_manager = RedisManager()
    mineru_client = MinerUClient()
    llm_client = LLMClient()
    group_ensured = False

    while not shutdown_requested:
        try:
            if not group_ensured:
                redis_manager.ensure_group()
                group_ensured = True
            message = redis_manager.read_task()
            if message is None:
                continue
            message_id, task = message
            process_task(redis_manager, mineru_client, llm_client, message_id, task)
        except Exception:
            logger.exception("Worker loop error; continuing after short backoff")
            group_ensured = False
            time.sleep(5)

    logger.info("Worker stopped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
