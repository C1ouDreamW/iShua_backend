package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskMetaVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 解答任务 Redis Stream 派发器
 * <p>
 * 与 {@link RedisStreamTaskDispatcher} 物理隔离：不同 Stream、不同消费组，互不影响。
 * </p>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnswerStreamTaskDispatcher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 应用启动时确保 Stream 和消费组存在（幂等）。
     */
    @PostConstruct
    public void initStream() {
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    IShuaRedisCacheConstants.ANSWER_STREAM_KEY,
                    IShuaRedisCacheConstants.ANSWER_STREAM_GROUP);
            log.info("[AnswerStream] 消费组已创建: {}", IShuaRedisCacheConstants.ANSWER_STREAM_GROUP);
        } catch (Exception e) {
            log.info("[AnswerStream] 消费组已存在或创建失败（可能已存在）: {}", e.getMessage());
        }
    }

    /**
     * 将解答任务元数据写入 Stream，返回 Stream entry ID。
     */
    public String dispatch(AiAnswerTaskMetaVO meta) {
        Map<String, String> fields;
        try {
            String json = objectMapper.writeValueAsString(meta);
            fields = Collections.singletonMap("payload", json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化解答任务元数据失败", e);
        }

        var recordId = stringRedisTemplate.opsForStream().add(
                IShuaRedisCacheConstants.ANSWER_STREAM_KEY,
                fields);
        String entryId = recordId.getValue();

        stringRedisTemplate.opsForStream().trim(
                IShuaRedisCacheConstants.ANSWER_STREAM_KEY,
                IShuaRedisCacheConstants.ANSWER_STREAM_MAX_LEN,
                true);

        log.info("[AnswerStream] 解答任务已入队 entryId={} answerTaskId={}", entryId, meta.getAnswerTaskId());
        return entryId;
    }
}
