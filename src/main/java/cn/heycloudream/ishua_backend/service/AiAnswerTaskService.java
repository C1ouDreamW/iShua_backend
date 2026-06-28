package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.entity.AiAnswerTask;
import cn.heycloudream.ishua_backend.enums.AiAnswerTaskStatus;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskStatusVO;
import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;

import java.util.List;
import java.util.Optional;

/**
 * AI 解答任务持久化服务。
 *
 * @author C1ouD
 */
public interface AiAnswerTaskService {

    void createOnSubmit(AiAnswerTask task);

    Optional<AiAnswerTask> findByAnswerTaskId(String answerTaskId);

    Optional<AiAnswerTaskStatusVO> buildStatus(String answerTaskId);

    Optional<List<QuestionPreviewVO>> readAnsweredQuestions(String answerTaskId);

    void syncStatusFromRedis(String answerTaskId, AiAnswerTaskStatusVO statusVO, List<QuestionPreviewVO> questions);

    void markStatus(String answerTaskId, AiAnswerTaskStatus target, String message, Integer answeredCount, Integer totalCount);

    void markImported(String answerTaskId, int importedCount);
}
