package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.enums.AiAnswerTaskStatus;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskStatusVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 管理 {@code ishua:answer:status:{answerTaskId}} 的读写（阶段 2）。
 * <p>
 * 含终态 CAS 保护：ANSWERED/PARTIAL 写入后，PROCESSING 不能覆盖；
 * IMPORTED/FAILED 写入后，任何状态都不能覆盖。
 * </p>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnswerTaskStatusStore {

    private static final int MAX_MESSAGE_CHARS = 500;

    /**
     * Lua 脚本：仅当当前 status 为期望值（ARGV[1]）时才覆盖为新的 JSON（ARGV[2]）。
     * KEYS[1] — 任务状态 Key
     * ARGV[1] — 期望的当前 status；若为 "*" 则跳过 status 校验
     * ARGV[2] — 新写入的完整 JSON 字符串
     * ARGV[3] — TTL 秒数
     * 返回 1 表示写入成功；0 表示状态不匹配未写入。
     */
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> CAS_WRITE_SCRIPT =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(
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
     * 写入任务状态（不含 questions 与 metrics）。
     */
    public void write(String answerTaskId, AiAnswerTaskStatus status, String message, Integer totalCount, Integer answeredCount) {
        AiAnswerTaskStatusVO vo = AiAnswerTaskStatusVO.builder()
                .answerTaskId(answerTaskId)
                .status(status.name())
                .message(truncate(message))
                .totalCount(totalCount)
                .answeredCount(answeredCount)
                .questions(null)
                .metrics(null)
                .build();
        writeSnapshot(answerTaskId, vo);
    }

    /**
     * 写入完整状态快照（含 questions 与 metrics，ANSWERED/PARTIAL 态使用）。
     */
    public void writeFull(String answerTaskId, AiAnswerTaskStatusVO vo) {
        writeSnapshot(answerTaskId, vo);
    }

    /**
     * 读取任务状态。
     */
    public Optional<AiAnswerTaskStatusVO> read(String answerTaskId) {
        String key = IShuaRedisCacheConstants.answerStatusKey(answerTaskId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AiAnswerTaskStatusVO.class));
        } catch (Exception e) {
            log.warn("[AnswerTaskId:{}] 读取解答任务状态 JSON 失败 key={}", answerTaskId, key, e);
            return Optional.empty();
        }
    }

    /**
     * 将任务标记为终态 FAILED（CAS：仅当当前为 PROCESSING 时才生效）。
     *
     * @return true 表示成功写入 FAILED；false 表示当前状态已不是 PROCESSING。
     */
    public boolean markFailedIfProcessing(String answerTaskId, String reason) {
        AiAnswerTaskStatusVO vo = AiAnswerTaskStatusVO.builder()
                .answerTaskId(answerTaskId)
                .status(AiAnswerTaskStatus.FAILED.name())
                .message(truncate(reason))
                .totalCount(null)
                .answeredCount(null)
                .questions(null)
                .metrics(null)
                .build();
        return casWrite(answerTaskId, vo, AiAnswerTaskStatus.PROCESSING.name());
    }

    private void writeSnapshot(String answerTaskId, AiAnswerTaskStatusVO vo) {
        AiAnswerTaskStatus target;
        try {
            target = AiAnswerTaskStatus.valueOf(vo.getStatus());
        } catch (IllegalArgumentException e) {
            log.warn("[AnswerTaskId:{}] 写入状态值非法 status={}", answerTaskId, vo.getStatus());
            return;
        }
        Optional<AiAnswerTaskStatus> currentOpt = read(answerTaskId)
                .map(c -> {
                    try {
                        return AiAnswerTaskStatus.valueOf(c.getStatus());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                });
        if (currentOpt.isPresent() && currentOpt.get().isTerminal() && currentOpt.get() != target) {
            log.info("[AnswerTaskId:{}] 当前已是终态 {}，拒绝覆盖为 {}", answerTaskId, currentOpt.get(), target);
            return;
        }

        String key = IShuaRedisCacheConstants.answerStatusKey(answerTaskId);
        try {
            String json = objectMapper.writeValueAsString(vo);
            stringRedisTemplate.opsForValue().set(
                    key,
                    json,
                    Duration.ofSeconds(IShuaRedisCacheConstants.ANSWER_STATUS_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.error("[AnswerTaskId:{}] 序列化解答任务状态失败", answerTaskId, e);
        }
    }

    private boolean casWrite(String answerTaskId, AiAnswerTaskStatusVO vo, String expectedCurrentStatus) {
        String key = IShuaRedisCacheConstants.answerStatusKey(answerTaskId);
        try {
            String json = objectMapper.writeValueAsString(vo);
            Long result = stringRedisTemplate.execute(
                    CAS_WRITE_SCRIPT,
                    java.util.List.of(key),
                    expectedCurrentStatus,
                    json,
                    String.valueOf(IShuaRedisCacheConstants.ANSWER_STATUS_TTL_SECONDS));
            return result != null && result == 1L;
        } catch (JsonProcessingException e) {
            log.error("[AnswerTaskId:{}] CAS 写入 FAILED 序列化失败", answerTaskId, e);
            return false;
        }
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() <= MAX_MESSAGE_CHARS ? trimmed : trimmed.substring(0, MAX_MESSAGE_CHARS) + "...";
    }
}
