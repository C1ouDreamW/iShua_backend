package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.exception.BusinessException;
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
 * 管理 {@code ishua:answer:result:{answerTaskId}} 的读写（阶段 2）。
 * <p>
 * 存储 LLM 解答后的题目列表（含 answerSource=AI_GENERATED 与 answerConfidence）。
 * </p>
 *
 * @author C1ouD
 */
@SuppressWarnings("null")
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnswerResultStore {

    private static final TypeReference<List<QuestionPreviewVO>> QUESTION_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 写入解答结果（题目列表）。
     */
    public void writeQuestions(String answerTaskId, List<QuestionPreviewVO> questions) {
        String key = IShuaRedisCacheConstants.answerResultKey(answerTaskId);
        try {
            String json = objectMapper.writeValueAsString(questions);
            stringRedisTemplate.opsForValue().set(
                    key,
                    json,
                    Duration.ofSeconds(IShuaRedisCacheConstants.ANSWER_RESULT_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.error("[AnswerTaskId:{}] 序列化解答结果失败", answerTaskId, e);
            throw new BusinessException(500, "序列化解答结果失败", e);
        }
    }

    /**
     * 读取解答结果（题目列表）。
     */
    public Optional<List<QuestionPreviewVO>> readQuestions(String answerTaskId) {
        String key = IShuaRedisCacheConstants.answerResultKey(answerTaskId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            List<QuestionPreviewVO> list = objectMapper.readValue(json, QUESTION_LIST_TYPE);
            return Optional.ofNullable(list);
        } catch (Exception e) {
            log.warn("[AnswerTaskId:{}] 读取解答结果 JSON 失败 key={}", answerTaskId, key, e);
            return Optional.empty();
        }
    }

    /**
     * 删除解答结果（入库完成后清理）。
     */
    public void delete(String answerTaskId) {
        String key = IShuaRedisCacheConstants.answerResultKey(answerTaskId);
        stringRedisTemplate.delete(key);
    }
}
