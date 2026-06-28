"""阶段 2 AI 解答 Worker 主循环（独立进程，与 worker.py 物理隔离）。

启动：python -m answer_worker

消费 ishua:answer:stream，调用 AnswerGenerator 分片+投票解答，
结果写入 ishua:answer:result 与 ishua:answer:status。
"""
from __future__ import annotations

import logging
import signal
import sys
import time
from typing import Any, Dict, List, Optional

from answer_generator import AnswerGenerator
from answer_redis_manager import AnswerRedisManager
from config import settings


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
    logger.info("Received signal %s, answer_worker will stop after current iteration", signum)


def require_field(task: Dict[str, Any], field: str) -> str:
    value = task.get(field)
    if value is None or str(value).strip() == "":
        raise ValueError(f"Answer task message missing required field: {field}")
    return str(value)


def process_task(
    redis_manager: AnswerRedisManager,
    generator: AnswerGenerator,
    message_id: str,
    task: Dict[str, Any],
) -> None:
    answer_task_id = require_field(task, "answerTaskId")
    questions: List[Dict[str, Any]] = task.get("questions") or []
    total_count = len(questions)
    metrics: Optional[Dict[str, int]] = None

    try:
        logger.info(
            "Accepted answer task message_id=%s answer_task_id=%s questions=%s",
            message_id,
            answer_task_id,
            total_count,
        )
        redis_manager.set_processing(answer_task_id, total_count)

        with redis_manager.heartbeat(answer_task_id):
            results, llm_ms, total_calls = generator.generate(questions)

        answered_count = sum(
            1 for r in results if r.get("answerSource") == "AI_GENERATED"
        )
        metrics = {"llmMs": llm_ms, "totalCalls": total_calls}

        if answered_count == 0:
            redis_manager.set_failed(
                answer_task_id,
                "所有题目解答失败，无可入库结果",
                metrics=metrics,
            )
            logger.warning(
                "Answer task all-failed answer_task_id=%s total=%s",
                answer_task_id,
                total_count,
            )
        elif answered_count < total_count:
            redis_manager.set_partial(
                answer_task_id,
                results,
                total_count=total_count,
                message=f"部分题目失败：成功 {answered_count}/{total_count}",
                metrics=metrics,
            )
            logger.info(
                "Answer task partial answer_task_id=%s answered=%s total=%s",
                answer_task_id,
                answered_count,
                total_count,
            )
        else:
            redis_manager.set_answered(
                answer_task_id,
                results,
                total_count=total_count,
                metrics=metrics,
            )
            logger.info(
                "Answer task answered answer_task_id=%s count=%s calls=%s",
                answer_task_id,
                answered_count,
                total_calls,
            )

        redis_manager.ack(message_id)
    except Exception as exc:
        logger.exception(
            "Answer task failed answer_task_id=%s message_id=%s",
            answer_task_id,
            message_id,
        )
        if answer_task_id:
            try:
                reason = str(exc).strip() or exc.__class__.__name__
                redis_manager.set_failed(answer_task_id, reason, metrics=metrics)
            except Exception:
                logger.exception(
                    "Failed to update FAILED status answer_task_id=%s",
                    answer_task_id,
                )
        try:
            redis_manager.ack(message_id)
            logger.info(
                "Failed answer task ACKed answer_task_id=%s message_id=%s",
                answer_task_id,
                message_id,
            )
        except Exception:
            logger.exception(
                "Failed to ACK failed answer task answer_task_id=%s message_id=%s",
                answer_task_id,
                message_id,
            )


def main() -> int:
    configure_logging()
    signal.signal(signal.SIGINT, request_shutdown)
    signal.signal(signal.SIGTERM, request_shutdown)

    settings.validate_answer()
    logger.info(
        "answer_worker starting shard_size=%s vote_rounds=%s model=%s",
        settings.answer_shard_size,
        settings.answer_vote_rounds,
        settings.answer_llm_model or settings.llm_model,
    )

    redis_manager = AnswerRedisManager()
    generator = AnswerGenerator()
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
            process_task(redis_manager, generator, message_id, task)
        except Exception:
            logger.exception("Answer worker loop error; continuing after short backoff")
            group_ensured = False
            time.sleep(5)

    logger.info("answer_worker stopped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
