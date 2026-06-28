package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.entity.AiAnswerTask;
import cn.heycloudream.ishua_backend.enums.AiAnswerTaskStatus;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.AiAnswerTaskMapper;
import cn.heycloudream.ishua_backend.service.AiAnswerTaskService;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerMetricsVO;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskStatusVO;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI 解答任务持久化服务实现（阶段 2）。
 * <p>
 * 状态机：SUBMITTED → PROCESSING → ANSWERED/PARTIAL → IMPORTED；任意环节可 FAILED。
 * </p>
 *
 * @author C1ouD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnswerTaskServiceImpl implements AiAnswerTaskService {

    private static final int MAX_MESSAGE_CHARS = 500;
    private static final TypeReference<List<QuestionPreviewVO>> QUESTION_LIST_TYPE = new TypeReference<>() {
    };

    private final AiAnswerTaskMapper aiAnswerTaskMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOnSubmit(AiAnswerTask task) {
        aiAnswerTaskMapper.insert(task);
    }

    @Override
    public Optional<AiAnswerTask> findByAnswerTaskId(String answerTaskId) {
        if (answerTaskId == null || answerTaskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(aiAnswerTaskMapper.selectOne(new LambdaQueryWrapper<AiAnswerTask>()
                .eq(AiAnswerTask::getAnswerTaskId, answerTaskId)));
    }

    @Override
    public Optional<AiAnswerTaskStatusVO> buildStatus(String answerTaskId) {
        return findByAnswerTaskId(answerTaskId).map(task -> {
            List<QuestionPreviewVO> questions = null;
            if (AiAnswerTaskStatus.ANSWERED.name().equals(task.getStatus())
                    || AiAnswerTaskStatus.PARTIAL.name().equals(task.getStatus())) {
                questions = readAnsweredQuestions(answerTaskId).orElse(null);
            }
            Integer answeredCount = task.getAnsweredCount();
            AiAnswerMetricsVO metrics = null;
            if (task.getLlmDurationMs() != null || task.getTotalCalls() != null) {
                metrics = AiAnswerMetricsVO.builder()
                        .llmMs(task.getLlmDurationMs())
                        .totalCalls(task.getTotalCalls())
                        .build();
            }
            return AiAnswerTaskStatusVO.builder()
                    .answerTaskId(task.getAnswerTaskId())
                    .status(task.getStatus())
                    .message(task.getErrorMessage())
                    .totalCount(task.getQuestionCount())
                    .answeredCount(answeredCount)
                    .questions(questions)
                    .metrics(metrics)
                    .build();
        });
    }

    @Override
    public Optional<List<QuestionPreviewVO>> readAnsweredQuestions(String answerTaskId) {
        return findByAnswerTaskId(answerTaskId)
                .map(AiAnswerTask::getAnsweredJson)
                .filter(json -> json != null && !json.isBlank())
                .flatMap(json -> parseAnsweredJson(answerTaskId, json));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncStatusFromRedis(String answerTaskId, AiAnswerTaskStatusVO statusVO, List<QuestionPreviewVO> questions) {
        if (statusVO == null || !AiAnswerTaskStatus.isValidCode(statusVO.getStatus())) {
            return;
        }
        AiAnswerTaskStatus target = AiAnswerTaskStatus.valueOf(statusVO.getStatus());
        AiAnswerMetricsVO metrics = statusVO.getMetrics();
        switch (target) {
            case ANSWERED, PARTIAL -> markAnswered(answerTaskId, statusVO.getMessage(), questions, metrics, target);
            default -> syncMarkStatus(answerTaskId, target, statusVO.getMessage(),
                    statusVO.getAnsweredCount(), statusVO.getTotalCount(), metrics);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markStatus(String answerTaskId, AiAnswerTaskStatus target, String message,
                           Integer answeredCount, Integer totalCount) {
        findByAnswerTaskId(answerTaskId).ifPresent(task ->
                updateStatusIfAllowed(task, target, message, answeredCount, totalCount, null, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markImported(String answerTaskId, int importedCount) {
        findByAnswerTaskId(answerTaskId).ifPresent(task -> updateStatusIfAllowed(
                task,
                AiAnswerTaskStatus.IMPORTED,
                "已导入 " + importedCount + " 道题",
                importedCount,
                task.getQuestionCount(),
                null,
                null));
    }

    private void markAnswered(
            String answerTaskId,
            String message,
            List<QuestionPreviewVO> questions,
            AiAnswerMetricsVO metrics,
            AiAnswerTaskStatus target) {
        // questions 为 null 时（Redis 结果已过期）跳过 answered_json 覆盖，避免把 DB 中已有数据写成 "null"
        if (questions == null) {
            log.info("[AiAnswerTask] questions 为空，跳过 {} 写入 answerTaskId={}", target, answerTaskId);
            return;
        }
        findByAnswerTaskId(answerTaskId).ifPresent(task -> {
            String answeredJson = toAnsweredJson(answerTaskId, questions);
            int answeredCount = questions.size();
            updateStatusIfAllowed(task, target, message, answeredCount, task.getQuestionCount(), answeredJson, metrics);
        });
    }

    private void syncMarkStatus(
            String answerTaskId,
            AiAnswerTaskStatus target,
            String message,
            Integer answeredCount,
            Integer totalCount,
            AiAnswerMetricsVO metrics) {
        findByAnswerTaskId(answerTaskId).ifPresent(task ->
                updateStatusIfAllowed(task, target, message, answeredCount, totalCount, null, metrics));
    }

    private void updateStatusIfAllowed(
            AiAnswerTask task,
            AiAnswerTaskStatus target,
            String message,
            Integer answeredCount,
            Integer totalCount,
            String answeredJson,
            AiAnswerMetricsVO metrics) {
        AiAnswerTaskStatus current = AiAnswerTaskStatus.valueOf(task.getStatus());
        if (current == target) {
            refreshSameStatus(task, target, message, answeredCount, totalCount, answeredJson, metrics);
            return;
        }
        if (current.isTerminal() || !current.canTransitionTo(target)) {
            log.info("[AiAnswerTask] 拒绝状态流转 answerTaskId={} {} -> {}", task.getAnswerTaskId(), current, target);
            return;
        }
        refreshSameStatus(task, target, message, answeredCount, totalCount, answeredJson, metrics);
    }

    private void refreshSameStatus(
            AiAnswerTask task,
            AiAnswerTaskStatus target,
            String message,
            Integer answeredCount,
            Integer totalCount,
            String answeredJson,
            AiAnswerMetricsVO metrics) {
        LocalDateTime now = LocalDateTime.now();
        AiAnswerTask update = new AiAnswerTask();
        update.setId(task.getId());
        update.setStatus(target.name());
        update.setErrorMessage(truncate(message));
        update.setAnsweredCount(answeredCount == null ? task.getAnsweredCount() : answeredCount);
        update.setQuestionCount(totalCount == null ? task.getQuestionCount() : totalCount);
        update.setUpdateTime(now);
        if (answeredJson != null) {
            update.setAnsweredJson(answeredJson);
        }
        applyMetrics(update, task, metrics);
        if ((target == AiAnswerTaskStatus.ANSWERED || target == AiAnswerTaskStatus.PARTIAL)
                && task.getAnsweredAt() == null) {
            update.setAnsweredAt(now);
        } else if (target == AiAnswerTaskStatus.IMPORTED && task.getImportedAt() == null) {
            update.setImportedAt(now);
        }
        aiAnswerTaskMapper.updateById(update);
        if (target == AiAnswerTaskStatus.IMPORTED) {
            aiAnswerTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiAnswerTask>()
                    .eq(AiAnswerTask::getId, task.getId())
                    .set(AiAnswerTask::getAnsweredJson, null));
            deleteRedisKeys(task.getAnswerTaskId());
        }
    }

    private static void applyMetrics(AiAnswerTask update, AiAnswerTask task, AiAnswerMetricsVO metrics) {
        if (metrics == null || task.getLlmDurationMs() != null) {
            return;
        }
        if (metrics.getLlmMs() != null) {
            update.setLlmDurationMs(metrics.getLlmMs());
        }
        if (metrics.getTotalCalls() != null) {
            update.setTotalCalls(metrics.getTotalCalls());
        }
    }

    private Optional<List<QuestionPreviewVO>> parseAnsweredJson(String answerTaskId, String json) {
        try {
            return Optional.of(objectMapper.readValue(json, QUESTION_LIST_TYPE));
        } catch (Exception e) {
            log.warn("[AiAnswerTask] 解析解答 JSON 失败 answerTaskId={}", answerTaskId, e);
            return Optional.empty();
        }
    }

    private String toAnsweredJson(String answerTaskId, List<QuestionPreviewVO> questions) {
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            log.error("[AiAnswerTask] 序列化解答结果失败 answerTaskId={}", answerTaskId, e);
            throw new BusinessException(500, "序列化解答结果失败", e);
        }
    }

    private void deleteRedisKeys(String answerTaskId) {
        stringRedisTemplate.delete(List.of(
                IShuaRedisCacheConstants.answerMetaKey(answerTaskId),
                IShuaRedisCacheConstants.answerStatusKey(answerTaskId),
                IShuaRedisCacheConstants.answerResultKey(answerTaskId),
                IShuaRedisCacheConstants.answerImportLockKey(answerTaskId)));
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() <= MAX_MESSAGE_CHARS ? trimmed : trimmed.substring(0, MAX_MESSAGE_CHARS) + "...";
    }
}
