package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 管理 {@code ishua:task:result:{taskId}} 的读写。
 * <p>
 * 存储 LLM 解析后的题目预览列表（JSON），供前端预览确认后批量落库。
 * </p>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImportResultStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 写入解析结果（题目预览列表）。
     */
    public void writeQuestions(String taskId, List<QuestionPreviewVO> questions) {
        String key = IShuaRedisCacheConstants.taskResultKey(taskId);
        try {
            String json = objectMapper.writeValueAsString(questions);
            stringRedisTemplate.opsForValue().set(
                    key,
                    json,
                    Duration.ofSeconds(IShuaRedisCacheConstants.TASK_RESULT_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.error("[TaskId:{}] 序列化解析结果失败", taskId, e);
            throw new RuntimeException("序列化解析结果失败", e);
        }
    }

    /**
     * 读取解析结果（题目预览列表）。
     */
    public Optional<List<QuestionPreviewVO>> readQuestions(String taskId) {
        String key = IShuaRedisCacheConstants.taskResultKey(taskId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            List<QuestionPreviewVO> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            return Optional.ofNullable(list);
        } catch (Exception e) {
            log.warn("[TaskId:{}] 读取解析结果 JSON 失败 key={}", taskId, key, e);
            return Optional.empty();
        }
    }

    /**
     * 删除解析结果（落库完成后清理）。
     */
    public void delete(String taskId) {
        String key = IShuaRedisCacheConstants.taskResultKey(taskId);
        stringRedisTemplate.delete(key);
    }
}
