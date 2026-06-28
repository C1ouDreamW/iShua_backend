package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.enums.AiAnswerTaskStatus;
import cn.heycloudream.ishua_backend.service.AiAnswerTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * 解答任务看门狗：定时扫描长期 PROCESSING 的解答任务，标记 FAILED。
 * <p>
 * 与 {@link AiImportTaskWatchdog} 物理隔离：扫描 {@code ishua:answer:status:*}，
 * 使用独立锁 {@code ishua:answer:watchdog:lock}。
 * </p>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnswerTaskWatchdog {

    private static final String SCAN_PATTERN = "ishua:answer:status:*";
    private static final String KEY_PREFIX = "ishua:answer:status:";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiAnswerTaskStatusStore statusStore;
    private final AiAnswerTaskMetaStore metaStore;
    private final AiAnswerTaskService aiAnswerTaskService;

    /**
     * 任务超时阈值（毫秒），默认 30 分钟。
     * <p>
     * 解答任务默认分片 10 + 投票 3 轮，20 题约 6 次调用，正常应在数分钟内完成；
     * 30 分钟足够覆盖大题量 + LLM 慢响应场景。
     * </p>
     */
    @Value("${ishua.ai-answer.task-timeout-ms:1800000}")
    private long taskTimeoutMs;

    /**
     * 每 2 分钟扫描一次。
     */
    @Scheduled(fixedDelay = 120_000)
    public void scan() {
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(IShuaRedisCacheConstants.ANSWER_WATCHDOG_LOCK_KEY,
                        "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            Set<String> keys = stringRedisTemplate.keys(SCAN_PATTERN);
            if (keys == null || keys.isEmpty()) {
                return;
            }
            int failedCount = 0;
            for (String key : keys) {
                String answerTaskId = key.substring(KEY_PREFIX.length());
                try {
                    if (checkAndFailIfStale(answerTaskId)) {
                        failedCount++;
                    }
                } catch (Exception e) {
                    log.warn("[AnswerWatchdog] 检查任务异常 answerTaskId={}", answerTaskId, e);
                }
            }
            if (failedCount > 0) {
                log.info("[AnswerWatchdog] 本轮标记超时解答任务 {} 个", failedCount);
            }
        } catch (Exception e) {
            log.error("[AnswerWatchdog] 扫描异常", e);
        }
    }

    private boolean checkAndFailIfStale(String answerTaskId) {
        Optional<AiAnswerTaskStatus> current = statusStore.read(answerTaskId)
                .map(vo -> {
                    try {
                        return AiAnswerTaskStatus.valueOf(vo.getStatus());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                });
        if (current.isEmpty() || current.get() != AiAnswerTaskStatus.PROCESSING) {
            return false;
        }

        Optional<Long> submittedAt = metaStore.read(answerTaskId)
                .map(AiAnswerTaskMetaVO -> AiAnswerTaskMetaVO.getSubmittedAt());

        if (submittedAt.isEmpty()) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - submittedAt.get();
        if (elapsed > taskTimeoutMs) {
            long elapsedMinutes = elapsed / 60_000;
            boolean marked = statusStore.markFailedIfProcessing(answerTaskId,
                    "解答任务处理超时（已运行 " + elapsedMinutes + " 分钟，阈值 " + (taskTimeoutMs / 60_000) + " 分钟）");
            if (marked) {
                aiAnswerTaskService.markStatus(answerTaskId, AiAnswerTaskStatus.FAILED,
                        "解答任务处理超时（已运行 " + elapsedMinutes + " 分钟，阈值 " + (taskTimeoutMs / 60_000) + " 分钟）",
                        null, null);
                log.warn("[AnswerWatchdog] 解答任务超时已标记 FAILED answerTaskId={} elapsedMinutes={}", answerTaskId, elapsedMinutes);
                return true;
            }
            log.info("[AnswerWatchdog] 标 FAILED 时发现状态已变更，跳过 answerTaskId={}", answerTaskId);
        }
        return false;
    }
}
