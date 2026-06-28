package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskMetaVO;
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
 * Redis Stream 任务派发器：将任务元数据写入 Stream，供 Python Worker 消费。
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamTaskDispatcher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 应用启动时确保 Stream 和消费组存在（幂等）。
     */
    @PostConstruct
    public void initStream() {
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    IShuaRedisCacheConstants.TASK_STREAM_KEY,
                    IShuaRedisCacheConstants.TASK_STREAM_GROUP);
            log.info("[Stream] 消费组已创建: {}", IShuaRedisCacheConstants.TASK_STREAM_GROUP);
        } catch (Exception e) {
            // BUSYGROUP — 消费组已存在，正常情况
            log.info("[Stream] 消费组已存在或创建失败（可能已存在）: {}", e.getMessage());
        }
    }

    /**
     * 将任务元数据写入 Stream，返回 Stream entry ID。
     *
     * @param meta 任务元数据
     * @return Stream entry ID（如 1684156800000-0）
     */
    public String dispatch(AiImportTaskMetaVO meta) {
        Map<String, String> fields;
        try {
            String json = objectMapper.writeValueAsString(meta);
            fields = Collections.singletonMap("payload", json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化任务元数据失败", e);
        }

        var recordId = stringRedisTemplate.opsForStream().add(
                IShuaRedisCacheConstants.TASK_STREAM_KEY,
                fields);
        String entryId = recordId.getValue();

        // 截断 Stream，保留最近 N 条
        stringRedisTemplate.opsForStream().trim(
                IShuaRedisCacheConstants.TASK_STREAM_KEY,
                IShuaRedisCacheConstants.TASK_STREAM_MAX_LEN,
                true);

        log.info("[Stream] 任务已入队 entryId={} taskId={}", entryId, meta.getTaskId());
        return entryId;
    }
}
