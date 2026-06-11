package cn.heycloudream.ishua_backend.service.ai;

import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.service.AiImportTaskService;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskStatusVO;
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
 * 将 Python Worker 写入 Redis 的任务状态同步到 MySQL 权威表。
 *
 * @author C1ouD
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ishua.ai-import.redis-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiImportTaskRedisSyncJob {

    private static final String STATUS_KEY_PREFIX = "ishua:task:status:";
    private static final String SCAN_PATTERN = STATUS_KEY_PREFIX + "*";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiImportTaskStatusStore statusStore;
    private final AiImportResultStore resultStore;
    private final AiImportTaskService aiImportTaskService;

    @Scheduled(fixedDelayString = "${ishua.ai-import.redis-sync.interval-ms:30000}")
    public void sync() {
        Set<String> keys = stringRedisTemplate.keys(SCAN_PATTERN);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        int synced = 0;
        for (String key : keys) {
            String taskId = key.substring(STATUS_KEY_PREFIX.length());
            try {
                if (syncOne(taskId)) {
                    synced++;
                }
            } catch (Exception e) {
                log.warn("[AiImportTaskRedisSyncJob] 同步任务状态失败 taskId={}", taskId, e);
            }
        }
        if (synced > 0) {
            log.info("[AiImportTaskRedisSyncJob] 本轮同步任务 {} 个", synced);
        }
    }

    private boolean syncOne(String taskId) {
        Optional<AiImportTaskStatusVO> statusOpt = statusStore.read(taskId);
        if (statusOpt.isEmpty()) {
            return false;
        }
        AiImportTaskStatusVO statusVO = statusOpt.get();
        if (!AiImportTaskStatus.isValidCode(statusVO.getStatus())) {
            return false;
        }
        List<QuestionPreviewVO> questions = null;
        if (AiImportTaskStatus.PARSED.name().equals(statusVO.getStatus())) {
            questions = resultStore.readQuestions(taskId).orElse(null);
            if (questions != null) {
                statusVO.setTotalCount(questions.size());
            }
        }
        aiImportTaskService.syncStatusFromRedis(taskId, statusVO, questions);
        return true;
    }
}
