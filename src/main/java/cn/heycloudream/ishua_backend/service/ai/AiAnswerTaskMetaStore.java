package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskMetaVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 管理 {@code ishua:answer:meta:{answerTaskId}} 的读写。
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnswerTaskMetaStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 写入解答任务元数据。
     */
    public void write(String answerTaskId, AiAnswerTaskMetaVO meta) {
        String key = IShuaRedisCacheConstants.answerMetaKey(answerTaskId);
        try {
            String json = objectMapper.writeValueAsString(meta);
            stringRedisTemplate.opsForValue().set(
                    key,
                    json,
                    Duration.ofSeconds(IShuaRedisCacheConstants.ANSWER_META_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.error("[AnswerTaskId:{}] 序列化解答任务元数据失败", answerTaskId, e);
        }
    }

    /**
     * 读取解答任务元数据。
     */
    public Optional<AiAnswerTaskMetaVO> read(String answerTaskId) {
        String key = IShuaRedisCacheConstants.answerMetaKey(answerTaskId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AiAnswerTaskMetaVO.class));
        } catch (Exception e) {
            log.warn("[AnswerTaskId:{}] 读取解答任务元数据 JSON 失败 key={}", answerTaskId, key, e);
            return Optional.empty();
        }
    }
}
