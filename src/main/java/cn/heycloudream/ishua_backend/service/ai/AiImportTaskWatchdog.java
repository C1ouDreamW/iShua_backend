package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
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
 * 任务看门狗：定时扫描长期处于 PROCESSING 状态的任务，标记为 FAILED。
 * <p>
 * 使用 Redis 分布式锁（SETNX）确保单实例执行。
 * </p>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImportTaskWatchdog {

    private static final String SCAN_PATTERN = "ishua:task:status:*";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiImportTaskStatusStore statusStore;
    private final AiImportTaskMetaStore metaStore;
    private final AiImportTaskService aiImportTaskService;

    /**
     * 任务超时阈值（毫秒），默认 30 分钟。
     * <p>
     * 必须 ≥ Python Worker 中 MinerU 轮询超时（默认 1800s）+ LLM 调用时间，否则会
     * 误将仍在正常处理中的长任务标为 FAILED，导致前端轮询出现 PROCESSING → FAILED →
     * PARSED 的状态抖动。
     * </p>
     */
    @Value("${ishua.ai-import.task-timeout-ms:1800000}")
    private long taskTimeoutMs;

    /**
     * 每 2 分钟扫描一次。
     */
    @Scheduled(fixedDelay = 120_000)
    public void scan() {
        // 获取分布式锁，防止多实例并发扫描
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(IShuaRedisCacheConstants.TASK_WATCHDOG_LOCK_KEY,
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
                String taskId = key.substring("ishua:task:status:".length());
                try {
                    if (checkAndFailIfStale(taskId)) {
                        failedCount++;
                    }
                } catch (Exception e) {
                    log.warn("[Watchdog] 检查任务异常 taskId={}", taskId, e);
                }
            }
            if (failedCount > 0) {
                log.info("[Watchdog] 本轮标记超时任务 {} 个", failedCount);
            }
        } catch (Exception e) {
            log.error("[Watchdog] 扫描异常", e);
        }
    }

    /**
     * 检查单个任务是否超时，若超时则标记 FAILED。
     *
     * @return true 表示标记了 FAILED
     */
    private boolean checkAndFailIfStale(String taskId) {
        Optional<AiImportTaskStatus> current = statusStore.read(taskId)
                .map(vo -> {
                    try {
                        return AiImportTaskStatus.valueOf(vo.getStatus());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                });
        if (current.isEmpty() || current.get() != AiImportTaskStatus.PROCESSING) {
            return false;
        }

        // 检查元数据中的提交时间
        Optional<Long> submittedAt = metaStore.read(taskId)
                .map(meta -> meta.getSubmittedAt());

        if (submittedAt.isEmpty()) {
            // 无法获取提交时间，保守处理：不标记
            return false;
        }

        long elapsed = System.currentTimeMillis() - submittedAt.get();
        if (elapsed > taskTimeoutMs) {
            long elapsedMinutes = elapsed / 60_000;
            // CAS：仅当当前仍为 PROCESSING 时才标 FAILED，避免覆盖 Worker 刚写入的 PARSED。
            boolean marked = statusStore.markFailedIfProcessing(taskId,
                    "任务处理超时（已运行 " + elapsedMinutes + " 分钟，阈值 " + (taskTimeoutMs / 60_000) + " 分钟）");
            if (marked) {
                aiImportTaskService.markStatus(taskId, AiImportTaskStatus.FAILED,
                        "任务处理超时（已运行 " + elapsedMinutes + " 分钟，阈值 " + (taskTimeoutMs / 60_000) + " 分钟）",
                        null);
                log.warn("[Watchdog] 任务超时已标记 FAILED taskId={} elapsedMinutes={}", taskId, elapsedMinutes);
                return true;
            }
            log.info("[Watchdog] 标 FAILED 时发现状态已变更，跳过 taskId={}", taskId);
        }
        return false;
    }
}
