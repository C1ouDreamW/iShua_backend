package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 导入接口限流器：基于 Redis Lua 脚本的滑动窗口计数器。
 * <p>
 * 限制单用户每时间窗口内的提交次数，防止资金消耗型 DDoS。
 * </p>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
public class AiImportRateLimiter {

    private static final String KEY_PREFIX = "ishua:ratelimit:ai_import:";

    /**
     * Lua 脚本：原子 INCR + EXPIRE，首次写入设置窗口过期。
     * KEYS[1] — 限流 Key
     * ARGV[1] — 上限次数
     * ARGV[2] — 窗口秒数
     * 返回 1 = 允许，0 = 拒绝
     */
    private static final DefaultRedisScript<Long> LUA_SCRIPT;

    static {
        LUA_SCRIPT = new DefaultRedisScript<>(
                """
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then
                    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
                end
                if current > tonumber(ARGV[1]) then
                    return 0
                end
                return 1
                """,
                Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${ishua.ai-import.rate-limit.max-requests-per-hour:5}")
    private int maxRequestsPerHour;

    public AiImportRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 检查并消耗一次配额。
     *
     * @param userId 用户 ID
     * @throws RateLimitException 配额耗尽时抛出
     */
    public void checkAndConsume(Long userId) {
        if (userId == null) {
            return; // 未登录用户不限制（实际上 Controller 层已拦截）
        }
        String key = KEY_PREFIX + userId;
        Long result = stringRedisTemplate.execute(
                LUA_SCRIPT,
                List.of(key),
                String.valueOf(maxRequestsPerHour),
                "3600" // 1 小时窗口
        );
        if (result == null || result == 0) {
            log.warn("[限流] 用户 {} AI 导入请求被限流拦截", userId);
            throw new RateLimitException("提交过于频繁，请稍后再试（每小时限 " + maxRequestsPerHour + " 次）");
        }
    }
}
