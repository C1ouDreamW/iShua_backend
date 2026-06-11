package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskMetaVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 管理 {@code ishua:task:meta:{taskId}} 的读写。
 * <p>
 * 存储任务元数据（userId、bankId、文件名等），供消费者和 Watchdog 使用。
 * </p>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImportTaskMetaStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 写入任务元数据。
     */
    public void write(String taskId, AiImportTaskMetaVO meta) {
        String key = IShuaRedisCacheConstants.taskMetaKey(taskId);
        try {
            String json = objectMapper.writeValueAsString(meta);
            stringRedisTemplate.opsForValue().set(
                    key,
                    json,
                    Duration.ofSeconds(IShuaRedisCacheConstants.TASK_META_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.error("[TaskId:{}] 序列化任务元数据失败", taskId, e);
        }
    }

    /**
     * 读取任务元数据。
     */
    public Optional<AiImportTaskMetaVO> read(String taskId) {
        String key = IShuaRedisCacheConstants.taskMetaKey(taskId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AiImportTaskMetaVO.class));
        } catch (Exception e) {
            log.warn("[TaskId:{}] 读取任务元数据 JSON 失败 key={}", taskId, key, e);
            return Optional.empty();
        }
    }
}
