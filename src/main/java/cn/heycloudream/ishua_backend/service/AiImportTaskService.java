package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.common.vo.PageResultVO;
import cn.heycloudream.ishua_backend.dto.admin.AdminAiImportCleanupDTO;
import cn.heycloudream.ishua_backend.dto.ai.AiImportTaskPageQueryDTO;
import cn.heycloudream.ishua_backend.entity.AiImportTask;
import cn.heycloudream.ishua_backend.enums.AiImportTaskStatus;
import cn.heycloudream.ishua_backend.vo.admin.AdminAiImportCleanupResultVO;
import cn.heycloudream.ishua_backend.vo.admin.AdminAiImportStatsVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskMetaVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskStatusVO;
import cn.heycloudream.ishua_backend.vo.ai.AiImportTaskSummaryVO;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;

import java.util.List;
import java.util.Optional;

/**
 * AI 导入任务持久化服务。
 *
 * @author C1ouD
 */
public interface AiImportTaskService {

    void createOnSubmit(AiImportTaskMetaVO meta);

    Optional<AiImportTask> findByTaskId(String taskId);

    Optional<AiImportTaskStatusVO> buildStatus(String taskId);

    PageResultVO<AiImportTaskSummaryVO> pageForUser(Long currentUserId, AiImportTaskPageQueryDTO query);

    Optional<List<QuestionPreviewVO>> readPreviewQuestions(String taskId);

    void syncStatusFromRedis(String taskId, AiImportTaskStatusVO statusVO, List<QuestionPreviewVO> questions);

    void markStatus(String taskId, AiImportTaskStatus target, String message, Integer questionCount);

    void markImported(String taskId, int importedCount);

    AdminAiImportCleanupResultVO cleanupStaleParsed(AdminAiImportCleanupDTO dto);

    AdminAiImportStatsVO getStats(int periodDays);
}
