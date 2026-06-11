package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 批量导入幂等服务：通过 Redis SETNX 锁防止同一 taskId 重复落库。
 * <p>
 * 锁有两种状态：
 * <ul>
 *   <li>{@code "locked"} — 落库进行中（短 TTL，落库失败可释放重试）；</li>
 *   <li>{@code "imported"} — 落库已成功（长 TTL，杜绝同一 taskId 在 meta TTL 期内重复落库）。</li>
 * </ul>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportIdempotentService {

    /** 落库进行中标记。 */
    static final String VALUE_LOCKED = "locked";

    /** 落库已完成标记。 */
    static final String VALUE_IMPORTED = "imported";

    /** 已完成标记的 TTL（秒）：24 小时，覆盖 meta TTL（1 小时）。 */
    static final int IMPORTED_TTL_SECONDS = 24 * 3600;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取导入锁，同一 taskId 只允许落库一次。
     *
     * @param taskId 任务 ID
     * @return true 表示首次导入，允许继续；false 表示已被其他请求获取
     */
    public boolean tryAcquire(String taskId) {
        String key = IShuaRedisCacheConstants.taskImportLockKey(taskId);
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, VALUE_LOCKED, Duration.ofSeconds(IShuaRedisCacheConstants.TASK_IMPORT_LOCK_TTL_SECONDS));
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 检查是否已经成功导入过（仅当锁值为 imported 时返回 true）。
     * <p>
     * 注意：仅 {@link #VALUE_LOCKED} 不视为"已导入"，落库失败 release 后允许重试。
     * </p>
     */
    public boolean isAlreadyImported(String taskId) {
        String key = IShuaRedisCacheConstants.taskImportLockKey(taskId);
        String value = stringRedisTemplate.opsForValue().get(key);
        return VALUE_IMPORTED.equals(value);
    }

    /**
     * 释放导入锁（落库失败时调用，允许用户重试）。
     * <p>
     * 仅当锁值为 {@code locked} 时才删除，避免误删 imported 终态标记。
     * </p>
     */
    public void release(String taskId) {
        String key = IShuaRedisCacheConstants.taskImportLockKey(taskId);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (VALUE_LOCKED.equals(value)) {
            stringRedisTemplate.delete(key);
        }
    }

    /**
     * 落库成功后改写为 imported 终态标记，TTL 延长至 24 小时，杜绝重复导入。
     * <p>
     * Redis 抖动时最多重试 3 次。若仍失败仅打印告警——此时若用户在 5 分钟内重试，
     * {@link #tryAcquire(String)} 会因为旧 locked 锁仍在而返回 false，外层抛 409；
     * 5 分钟后旧锁过期且用户重试时存在重复导入风险，但概率极低。
     * </p>
     */
    public void markImported(String taskId) {
        String key = IShuaRedisCacheConstants.taskImportLockKey(taskId);
        Duration ttl = Duration.ofSeconds(IMPORTED_TTL_SECONDS);
        RuntimeException lastErr = null;
        for (int i = 0; i < 3; i++) {
            try {
                stringRedisTemplate.opsForValue().set(key, VALUE_IMPORTED, ttl);
                return;
            } catch (RuntimeException e) {
                lastErr = e;
                log.warn("[Idempotent] markImported 失败，重试 #{} taskId={} err={}", i + 1, taskId, e.getMessage());
            }
        }
        log.error("[Idempotent] markImported 终态标记失败 taskId={}", taskId, lastErr);
    }
}
