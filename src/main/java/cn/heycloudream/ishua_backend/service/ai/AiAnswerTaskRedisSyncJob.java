package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.enums.AiAnswerTaskStatus;
import cn.heycloudream.ishua_backend.service.AiAnswerTaskService;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskStatusVO;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 将 Python 解答 Worker 写入 Redis 的任务状态同步到 MySQL（阶段 2）。
 * <p>
 * 与 {@link AiImportTaskRedisSyncJob} 物理隔离：扫描 {@code ishua:answer:status:*}。
 * </p>
 *
 * @author C1ouD
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ishua.ai-answer.redis-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAnswerTaskRedisSyncJob {

    private static final String STATUS_KEY_PREFIX = "ishua:answer:status:";
    private static final String SCAN_PATTERN = STATUS_KEY_PREFIX + "*";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiAnswerTaskStatusStore statusStore;
    private final AiAnswerResultStore resultStore;
    private final AiAnswerTaskService aiAnswerTaskService;

    @Scheduled(fixedDelayString = "${ishua.ai-answer.redis-sync.interval-ms:30000}")
    public void sync() {
        Set<String> keys = stringRedisTemplate.keys(SCAN_PATTERN);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        int synced = 0;
        for (String key : keys) {
            String answerTaskId = key.substring(STATUS_KEY_PREFIX.length());
            try {
                if (syncOne(answerTaskId)) {
                    synced++;
                }
            } catch (Exception e) {
                log.warn("[AiAnswerTaskRedisSyncJob] 同步解答任务状态失败 answerTaskId={}", answerTaskId, e);
            }
        }
        if (synced > 0) {
            log.info("[AiAnswerTaskRedisSyncJob] 本轮同步解答任务 {} 个", synced);
        }
    }

    private boolean syncOne(String answerTaskId) {
        Optional<AiAnswerTaskStatusVO> statusOpt = statusStore.read(answerTaskId);
        if (statusOpt.isEmpty()) {
            return false;
        }
        AiAnswerTaskStatusVO statusVO = statusOpt.get();
        if (!AiAnswerTaskStatus.isValidCode(statusVO.getStatus())) {
            return false;
        }
        List<QuestionPreviewVO> questions = null;
        if (AiAnswerTaskStatus.ANSWERED.name().equals(statusVO.getStatus())
                || AiAnswerTaskStatus.PARTIAL.name().equals(statusVO.getStatus())) {
            questions = resultStore.readQuestions(answerTaskId).orElse(null);
            if (questions != null) {
                statusVO.setAnsweredCount(questions.size());
            }
        }
        aiAnswerTaskService.syncStatusFromRedis(answerTaskId, statusVO, questions);
        return true;
    }
}
