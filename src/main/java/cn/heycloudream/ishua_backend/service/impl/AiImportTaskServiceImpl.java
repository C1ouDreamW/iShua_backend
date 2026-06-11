package cn.heycloudream.ishua_backend.service.impl;

import cn.heycloudream.ishua_backend.common.constants.IShuaRedisCacheConstants;
import cn.heycloudream.ishua_backend.common.vo.PageResultVO;
import cn.heycloudream.ishua_backend.dto.admin.AdminAiImportCleanupDTO;
import cn.heycloudream.ishua_backend.dto.ai.AiImportTaskPageQueryDTO;
import cn.heycloudream.ishua_backend.entity.AiImportTask;
import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.exception.BusinessException;
import cn.heycloudream.ishua_backend.mapper.AiImportTaskMapper;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.service.file.FileStorageService;
import cn.heycloudream.ishua_backend.mapper.row.AiImportTaskOverallStatsRow;
import cn.heycloudream.ishua_backend.mapper.row.AiImportTaskStatusAggRow;
import cn.heycloudream.ishua_backend.vo.admin.AdminAiImportCleanupResultVO;
import cn.heycloudream.ishua_backend.vo.admin.AdminAiImportStatsVO;
import cn.heycloudream.ishua_backend.vo.admin.AdminAiImportStatusStatVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskMetaVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskPipelineMetricsVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskStatusVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskSummaryVO;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 导入任务持久化服务实现。
 *
 * @author C1ouD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImportTaskServiceImpl implements AiImportTaskService {

    private static final int MAX_MESSAGE_CHARS = 500;
    private static final int DEFAULT_CLEANUP_DAYS = 7;
    private static final int DEFAULT_CLEANUP_BATCH = 200;
    private static final int DEFAULT_STATS_PERIOD_DAYS = 30;
    private static final int MIN_STATS_PERIOD_DAYS = 1;
    private static final int MAX_STATS_PERIOD_DAYS = 365;
    private static final TypeReference<List<QuestionPreviewVO>> QUESTION_PREVIEW_LIST_TYPE = new TypeReference<>() {
    };

    private final AiImportTaskMapper aiImportTaskMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileStorageService fileStorageService;

    @Value("${ishua.ai-import.cleanup-default-older-than-days:7}")
    private int cleanupDefaultOlderThanDays;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOnSubmit(AiImportTaskMetaVO meta) {
        LocalDateTime now = LocalDateTime.now();
        AiImportTask task = AiImportTask.builder()
                .taskId(meta.getTaskId())
                .userId(meta.getUserId())
                .bankId(meta.getBankId())
                .status(AiImportTaskStatus.SUBMITTED.name())
                .fileName(meta.getFileName())
                .fileSize(meta.getFileSize())
                .fileUrl(meta.getFileUrl())
                .importType(meta.getType() == null ? "file" : meta.getType())
                .submittedAt(toLocalDateTime(meta.getSubmittedAt()))
                .createTime(now)
                .updateTime(now)
                .isDeleted(0)
                .build();
        aiImportTaskMapper.insert(task);
    }

    @Override
    public Optional<AiImportTask> findByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(aiImportTaskMapper.selectOne(new LambdaQueryWrapper<AiImportTask>()
                .eq(AiImportTask::getTaskId, taskId)));
    }

    @Override
    public Optional<AiImportTaskStatusVO> buildStatus(String taskId) {
        return findByTaskId(taskId).map(task -> {
            List<QuestionPreviewVO> questions = null;
            if (AiImportTaskStatus.PARSED.name().equals(task.getStatus())) {
                questions = readPreviewQuestions(taskId).orElse(null);
            }
            return AiImportTaskStatusVO.builder()
                    .taskId(task.getTaskId())
                    .status(task.getStatus())
                    .message(task.getErrorMessage())
                    .totalCount(task.getQuestionCount())
                    .questions(questions)
                    .build();
        });
    }

    @Override
    public PageResultVO<AiImportTaskSummaryVO> pageForUser(Long currentUserId, AiImportTaskPageQueryDTO query) {
        List<String> statuses = parseStatuses(query.getStatus());
        Page<AiImportTask> page = new Page<>(query.getCurrent(), query.getPageSize());
        LambdaQueryWrapper<AiImportTask> wrapper = new LambdaQueryWrapper<AiImportTask>()
                .eq(AiImportTask::getUserId, currentUserId)
                .eq(query.getBankId() != null, AiImportTask::getBankId, query.getBankId())
                .in(!statuses.isEmpty(), AiImportTask::getStatus, statuses)
                .orderByDesc(AiImportTask::getSubmittedAt);
        aiImportTaskMapper.selectPage(page, wrapper);
        boolean includePreview = Boolean.TRUE.equals(query.getIncludePreview());
        List<AiImportTaskSummaryVO> records = page.getRecords().stream()
                .map(task -> toSummary(task, includePreview))
                .collect(Collectors.toList());
        return PageResultVO.<AiImportTaskSummaryVO>builder()
                .total(page.getTotal())
                .records(records)
                .build();
    }

    @Override
    public Optional<List<QuestionPreviewVO>> readPreviewQuestions(String taskId) {
        return findByTaskId(taskId)
                .map(AiImportTask::getPreviewJson)
                .filter(json -> json != null && !json.isBlank())
                .flatMap(json -> parsePreviewJson(taskId, json));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncStatusFromRedis(String taskId, AiImportTaskStatusVO statusVO, List<QuestionPreviewVO> questions) {
        if (statusVO == null || !AiImportTaskStatus.isValidCode(statusVO.getStatus())) {
            return;
        }
        AiImportTaskStatus target = AiImportTaskStatus.valueOf(statusVO.getStatus());
        AiImportTaskPipelineMetricsVO metrics = statusVO.getMetrics();
        if (target == AiImportTaskStatus.PARSED) {
            List<QuestionPreviewVO> preview = questions != null ? questions : List.of();
            markParsed(taskId, statusVO.getMessage(), preview, metrics);
            return;
        }
        syncMarkStatus(taskId, target, statusVO.getMessage(), statusVO.getTotalCount(), metrics);
    }

    private void syncMarkStatus(
            String taskId,
            AiImportTaskStatus target,
            String message,
            Integer questionCount,
            AiImportTaskPipelineMetricsVO metrics) {
        findByTaskId(taskId).ifPresent(task ->
                updateStatusIfAllowed(task, target, message, questionCount, null, metrics));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markStatus(String taskId, AiImportTaskStatus target, String message, Integer questionCount) {
        findByTaskId(taskId).ifPresent(task ->
                updateStatusIfAllowed(task, target, message, questionCount, null, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markImported(String taskId, int importedCount) {
        findByTaskId(taskId).ifPresent(task -> updateStatusIfAllowed(
                task,
                AiImportTaskStatus.IMPORTED,
                "已导入 " + importedCount + " 道题",
                importedCount,
                null,
                null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAiImportCleanupResultVO cleanupStaleParsed(AdminAiImportCleanupDTO dto) {
        int olderThanDays = dto.getOlderThanDays() == null ? cleanupDefaultOlderThanDays : dto.getOlderThanDays();
        if (olderThanDays <= 0) {
            olderThanDays = DEFAULT_CLEANUP_DAYS;
        }
        int maxBatch = dto.getMaxBatch() == null ? DEFAULT_CLEANUP_BATCH : dto.getMaxBatch();
        boolean dryRun = dto.getDryRun() == null || dto.getDryRun();
        boolean deleteFiles = Boolean.TRUE.equals(dto.getDeleteFiles());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);

        LambdaQueryWrapper<AiImportTask> base = cleanupWrapper(dto, cutoff);
        Long matchedCount = aiImportTaskMapper.selectCount(base);
        List<AiImportTask> tasks = aiImportTaskMapper.selectList(cleanupWrapper(dto, cutoff)
                .orderByAsc(AiImportTask::getParsedAt)
                .last("LIMIT " + maxBatch));
        List<String> sampleTaskIds = tasks.stream()
                .limit(10)
                .map(AiImportTask::getTaskId)
                .collect(Collectors.toList());
        if (dryRun) {
            return AdminAiImportCleanupResultVO.builder()
                    .dryRun(true)
                    .matchedCount(matchedCount)
                    .processedCount(0)
                    .sampleTaskIds(sampleTaskIds)
                    .message("dryRun：未执行写操作")
                    .build();
        }

        int processed = 0;
        for (AiImportTask task : tasks) {
            int updated = aiImportTaskMapper.update(null, new LambdaUpdateWrapper<AiImportTask>()
                    .eq(AiImportTask::getId, task.getId())
                    .eq(AiImportTask::getStatus, AiImportTaskStatus.PARSED.name())
                    .set(AiImportTask::getStatus, AiImportTaskStatus.EXPIRED.name())
                    .set(AiImportTask::getExpiredAt, LocalDateTime.now())
                    .set(AiImportTask::getErrorMessage, "长时间未确认，已清理")
                    .set(AiImportTask::getPreviewJson, null)
                    .set(AiImportTask::getUpdateTime, LocalDateTime.now()));
            if (updated > 0) {
                processed++;
                deleteRedisKeys(task.getTaskId());
                if (deleteFiles && task.getFileUrl() != null && !task.getFileUrl().isBlank()) {
                    fileStorageService.delete(task.getFileUrl());
                }
            }
        }
        return AdminAiImportCleanupResultVO.builder()
                .dryRun(false)
                .matchedCount(matchedCount)
                .processedCount(processed)
                .sampleTaskIds(sampleTaskIds)
                .message("已清理 " + processed + " 个长时间未确认任务")
                .build();
    }

    @Override
    public AdminAiImportStatsVO getStats(int periodDays) {
        int normalizedDays = normalizeStatsPeriodDays(periodDays);
        LocalDateTime periodEnd = LocalDateTime.now();
        LocalDateTime periodStart = periodEnd.minusDays(normalizedDays);

        AiImportTaskOverallStatsRow overall = aiImportTaskMapper.selectOverallStatsSince(periodStart);
        List<AiImportTaskStatusAggRow> statusRows = aiImportTaskMapper.selectStatusAggSince(periodStart);
        Map<String, AiImportTaskStatusAggRow> rowByStatus = statusRows.stream()
                .collect(Collectors.toMap(AiImportTaskStatusAggRow::getStatus, Function.identity(), (a, b) -> a));

        List<AdminAiImportStatusStatVO> statusStats = EnumSet.allOf(AiImportTaskStatus.class).stream()
                .map(status -> toStatusStat(status.name(), rowByStatus.get(status.name())))
                .collect(Collectors.toList());

        long totalTasks = overall == null || overall.getTotalCount() == null ? 0L : overall.getTotalCount();
        long failedCount = overall == null || overall.getFailedCount() == null ? 0L : overall.getFailedCount();

        return AdminAiImportStatsVO.builder()
                .periodDays(normalizedDays)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalTasks(totalTasks)
                .statusStats(statusStats)
                .dailyAvgSubmitCount(totalTasks == 0L ? 0D : (double) totalTasks / normalizedDays)
                .avgPipelineSeconds(overall == null ? null : overall.getAvgPipelineSec())
                .avgMineruSeconds(overall == null ? null : overall.getAvgMineruSec())
                .avgLlmSeconds(overall == null ? null : overall.getAvgLlmSec())
                .avgParseSeconds(overall == null ? null : overall.getAvgPipelineSec())
                .avgQuestionCount(overall == null ? null : overall.getAvgQuestionCount())
                .failureRate(totalTasks == 0L ? 0D : (double) failedCount / totalTasks)
                .build();
    }

    private AdminAiImportStatusStatVO toStatusStat(String status, AiImportTaskStatusAggRow row) {
        if (row == null) {
            return AdminAiImportStatusStatVO.builder()
                    .status(status)
                    .count(0L)
                    .avgParseSeconds(null)
                    .build();
        }
        return AdminAiImportStatusStatVO.builder()
                .status(status)
                .count(row.getCnt() == null ? 0L : row.getCnt())
                .avgParseSeconds(row.getAvgParseSec())
                .build();
    }

    private static int normalizeStatsPeriodDays(int periodDays) {
        if (periodDays < MIN_STATS_PERIOD_DAYS) {
            return DEFAULT_STATS_PERIOD_DAYS;
        }
        return Math.min(periodDays, MAX_STATS_PERIOD_DAYS);
    }

    private void markParsed(
            String taskId,
            String message,
            List<QuestionPreviewVO> questions,
            AiImportTaskPipelineMetricsVO metrics) {
        findByTaskId(taskId).ifPresent(task -> {
            String previewJson = toPreviewJson(taskId, questions);
            updateStatusIfAllowed(task, AiImportTaskStatus.PARSED, message, questions.size(), previewJson, metrics);
        });
    }

    private void updateStatusIfAllowed(
            AiImportTask task,
            AiImportTaskStatus target,
            String message,
            Integer questionCount,
            String previewJson,
            AiImportTaskPipelineMetricsVO metrics) {
        AiImportTaskStatus current = AiImportTaskStatus.valueOf(task.getStatus());
        if (current == target) {
            refreshSameStatus(task, target, message, questionCount, previewJson, metrics);
            return;
        }
        if (current.isTerminal() || !current.canTransitionTo(target)) {
            log.info("[AiImportTask] 拒绝状态流转 taskId={} {} -> {}", task.getTaskId(), current, target);
            return;
        }
        refreshSameStatus(task, target, message, questionCount, previewJson, metrics);
    }

    private void refreshSameStatus(
            AiImportTask task,
            AiImportTaskStatus target,
            String message,
            Integer questionCount,
            String previewJson,
            AiImportTaskPipelineMetricsVO metrics) {
        LocalDateTime now = LocalDateTime.now();
        AiImportTask update = new AiImportTask();
        update.setId(task.getId());
        update.setStatus(target.name());
        update.setErrorMessage(truncate(message));
        update.setQuestionCount(questionCount == null ? task.getQuestionCount() : questionCount);
        update.setUpdateTime(now);
        if (previewJson != null) {
            update.setPreviewJson(previewJson);
        }
        applyPipelineMetrics(update, task, metrics);
        if (target == AiImportTaskStatus.PARSED && task.getParsedAt() == null) {
            update.setParsedAt(now);
        } else if (target == AiImportTaskStatus.IMPORTED && task.getImportedAt() == null) {
            update.setImportedAt(now);
        } else if (target == AiImportTaskStatus.EXPIRED && task.getExpiredAt() == null) {
            update.setExpiredAt(now);
        }
        aiImportTaskMapper.updateById(update);
        if (target == AiImportTaskStatus.IMPORTED) {
            aiImportTaskMapper.update(null, new LambdaUpdateWrapper<AiImportTask>()
                    .eq(AiImportTask::getId, task.getId())
                    .set(AiImportTask::getPreviewJson, null));
        }
    }

    private static void applyPipelineMetrics(
            AiImportTask update,
            AiImportTask task,
            AiImportTaskPipelineMetricsVO metrics) {
        if (metrics == null || task.getPipelineDurationMs() != null) {
            return;
        }
        if (metrics.getMineruMs() != null) {
            update.setMineruDurationMs(metrics.getMineruMs());
        }
        if (metrics.getLlmMs() != null) {
            update.setLlmDurationMs(metrics.getLlmMs());
        }
        if (metrics.getPipelineMs() != null) {
            update.setPipelineDurationMs(metrics.getPipelineMs());
        } else if (metrics.getMineruMs() != null || metrics.getLlmMs() != null) {
            int mineru = metrics.getMineruMs() == null ? 0 : metrics.getMineruMs();
            int llm = metrics.getLlmMs() == null ? 0 : metrics.getLlmMs();
            update.setPipelineDurationMs(mineru + llm);
        }
    }

    private AiImportTaskSummaryVO toSummary(AiImportTask task, boolean includePreview) {
        List<QuestionPreviewVO> questions = null;
        if (includePreview && AiImportTaskStatus.PARSED.name().equals(task.getStatus())) {
            questions = parsePreviewJson(task.getTaskId(), task.getPreviewJson()).orElse(null);
        }
        return AiImportTaskSummaryVO.builder()
                .taskId(task.getTaskId())
                .bankId(task.getBankId())
                .fileName(task.getFileName())
                .status(task.getStatus())
                .message(task.getErrorMessage())
                .questionCount(task.getQuestionCount())
                .submittedAt(task.getSubmittedAt())
                .parsedAt(task.getParsedAt())
                .importedAt(task.getImportedAt())
                .expiredAt(task.getExpiredAt())
                .questions(questions)
                .build();
    }

    private LambdaQueryWrapper<AiImportTask> cleanupWrapper(AdminAiImportCleanupDTO dto, LocalDateTime cutoff) {
        return new LambdaQueryWrapper<AiImportTask>()
                .eq(AiImportTask::getStatus, AiImportTaskStatus.PARSED.name())
                .lt(AiImportTask::getParsedAt, cutoff)
                .eq(dto.getBankId() != null, AiImportTask::getBankId, dto.getBankId())
                .eq(dto.getUserId() != null, AiImportTask::getUserId, dto.getUserId());
    }

    private List<String> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .peek(s -> {
                    if (!AiImportTaskStatus.isValidCode(s)) {
                        throw new BusinessException(400, "非法任务状态：" + s);
                    }
                })
                .collect(Collectors.toList());
    }

    private Optional<List<QuestionPreviewVO>> parsePreviewJson(String taskId, String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, QUESTION_PREVIEW_LIST_TYPE));
        } catch (Exception e) {
            log.warn("[AiImportTask] 解析预览 JSON 失败 taskId={}", taskId, e);
            return Optional.empty();
        }
    }

    private String toPreviewJson(String taskId, List<QuestionPreviewVO> questions) {
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            log.error("[AiImportTask] 序列化预览题目失败 taskId={}", taskId, e);
            throw new BusinessException(500, "序列化预览题目失败");
        }
    }

    private void deleteRedisKeys(String taskId) {
        stringRedisTemplate.delete(List.of(
                IShuaRedisCacheConstants.taskMetaKey(taskId),
                IShuaRedisCacheConstants.taskStatusKey(taskId),
                IShuaRedisCacheConstants.taskResultKey(taskId),
                IShuaRedisCacheConstants.taskImportLockKey(taskId)));
    }

    private static LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() <= MAX_MESSAGE_CHARS ? trimmed : trimmed.substring(0, MAX_MESSAGE_CHARS) + "...";
    }
}
