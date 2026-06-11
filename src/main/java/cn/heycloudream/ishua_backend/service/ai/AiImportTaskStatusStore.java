package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskStatusVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * 管理 {@code ishua:task:status:{taskId}} 的读写，供前端轮询任务状态。
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImportTaskStatusStore {

    private static final int MAX_MESSAGE_CHARS = 500;

    /**
     * Lua 脚本：仅当当前 status 为期望值（ARGV[1]）时才覆盖为新的 JSON（ARGV[2]）。
     * <p>
     * KEYS[1] — 任务状态 Key
     * ARGV[1] — 期望的当前 status（如 "PROCESSING"）；若为 "*" 则跳过 status 校验
     * ARGV[2] — 新写入的完整 JSON 字符串
     * ARGV[3] — TTL 秒数
     * 返回 1 表示写入成功；0 表示状态不匹配未写入。
     * </p>
     * 用 Jackson 解析的话需要原 JSON，这里用 cjson 简单提取 status 字段。
     */
    private static final DefaultRedisScript<Long> CAS_WRITE_SCRIPT = new DefaultRedisScript<>(
            "local cur = redis.call('GET', KEYS[1])\n"
                    + "if cur and ARGV[1] ~= '*' then\n"
                    + "    local ok, decoded = pcall(cjson.decode, cur)\n"
                    + "    if ok and decoded and decoded.status ~= ARGV[1] then\n"
                    + "        return 0\n"
                    + "    end\n"
                    + "end\n"
                    + "redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])\n"
                    + "return 1\n",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 写入任务状态。
     */
    public void write(String taskId, AiImportTaskStatus status, String message, Integer totalCount) {
        AiImportTaskStatusVO vo = AiImportTaskStatusVO.builder()
                .taskId(taskId)
                .status(status.name())
                .message(truncate(message))
                .totalCount(totalCount)
                .questions(null) // 状态快照不携带预览题列表
                .build();
        write(taskId, vo);
    }

    /**
     * 写入完整状态快照（含预览题列表，仅 PARSED 态使用）。
     */
    public void writeFull(String taskId, AiImportTaskStatusVO vo) {
        write(taskId, vo);
    }

    /**
     * 读取任务状态。
     */
    public Optional<AiImportTaskStatusVO> read(String taskId) {
        String key = IShuaRedisCacheConstants.taskStatusKey(taskId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AiImportTaskStatusVO.class));
        } catch (Exception e) {
            log.warn("[TaskId:{}] 读取任务状态 JSON 失败 key={}", taskId, key, e);
            return Optional.empty();
        }
    }

    /**
     * 将任务标记为终态 FAILED（CAS：仅当当前为 PROCESSING 时才生效）。
     * <p>
     * 这是 Watchdog 等兜底场景使用的接口；普通业务流程请用 {@link #write}。
     * </p>
     *
     * @return true 表示成功写入 FAILED；false 表示当前状态已不是 PROCESSING（如 Worker 已写 PARSED），未覆盖。
     */
    public boolean markFailedIfProcessing(String taskId, String reason) {
        AiImportTaskStatusVO vo = AiImportTaskStatusVO.builder()
                .taskId(taskId)
                .status(AiImportTaskStatus.FAILED.name())
                .message(truncate(reason))
                .totalCount(null)
                .questions(null)
                .build();
        return casWrite(taskId, vo, AiImportTaskStatus.PROCESSING.name());
    }

    private void write(String taskId, AiImportTaskStatusVO vo) {
        // 终态保护：若当前已是 IMPORTED/FAILED，禁止任何业务流程覆盖（避免抖动）。
        AiImportTaskStatus target;
        try {
            target = AiImportTaskStatus.valueOf(vo.getStatus());
        } catch (IllegalArgumentException e) {
            log.warn("[TaskId:{}] 写入状态值非法 status={}", taskId, vo.getStatus());
            return;
        }
        Optional<AiImportTaskStatus> currentOpt = read(taskId)
                .map(c -> {
                    try {
                        return AiImportTaskStatus.valueOf(c.getStatus());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                });
        if (currentOpt.isPresent() && currentOpt.get().isTerminal() && currentOpt.get() != target) {
            log.info("[TaskId:{}] 当前已是终态 {}，拒绝覆盖为 {}", taskId, currentOpt.get(), target);
            return;
        }

        String key = IShuaRedisCacheConstants.taskStatusKey(taskId);
        try {
            String json = objectMapper.writeValueAsString(vo);
            stringRedisTemplate.opsForValue().set(
                    key,
                    json,
                    Duration.ofSeconds(IShuaRedisCacheConstants.TASK_STATUS_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.error("[TaskId:{}] 序列化任务状态失败", taskId, e);
        }
    }

    /**
     * 在 Redis 端用 Lua 做"仅当当前 status==expected 时才写新值"的原子比对。
     */
    private boolean casWrite(String taskId, AiImportTaskStatusVO vo, String expectedStatus) {
        String key = IShuaRedisCacheConstants.taskStatusKey(taskId);
        String json;
        try {
            json = objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            log.error("[TaskId:{}] 序列化任务状态失败", taskId, e);
            return false;
        }
        Long ok = stringRedisTemplate.execute(
                CAS_WRITE_SCRIPT,
                Collections.singletonList(key),
                expectedStatus,
                json,
                String.valueOf(IShuaRedisCacheConstants.TASK_STATUS_TTL_SECONDS));
        return ok != null && ok == 1L;
    }

    private static String truncate(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        String t = message.trim();
        return t.length() <= MAX_MESSAGE_CHARS ? t : t.substring(0, MAX_MESSAGE_CHARS) + "...";
    }
}
