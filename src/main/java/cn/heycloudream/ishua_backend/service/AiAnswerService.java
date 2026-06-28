package cn.heycloudream.ishua_backend.service;

import cn.heycloudream.ishua_backend.dto.ai.AiAnswerCreateDTO;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerSubmitVO;
import cn.heycloudream.ishua_backend.vo.ai.AiAnswerTaskStatusVO;

/**
 * AI 解答业务编排服务。
 *
 * @author C1ouD
 */
public interface AiAnswerService {

    /**
     * 创建解答任务：校验父任务归属 → 取 preview_json → 筛选 MISSING 客观题 →
     * 写 MySQL → 写 Redis meta/status → 投递 Stream。
     */
    AiAnswerSubmitVO createAnswerTask(Long currentUserId, String parentTaskId, AiAnswerCreateDTO dto);

    /**
     * 查询解答任务状态。优先 MySQL，Redis 兜底。
     */
    AiAnswerTaskStatusVO getStatus(Long currentUserId, String parentTaskId, String answerTaskId);
}
