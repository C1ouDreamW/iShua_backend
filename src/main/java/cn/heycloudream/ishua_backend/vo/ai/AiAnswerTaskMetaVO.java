package cn.heycloudream.ishua_backend.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 解答任务元数据，写入 Redis 供消费者和 Watchdog 使用。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnswerTaskMetaVO {

    /** 解答任务 ID（UUID）。 */
    private String answerTaskId;

    /** 关联的 ai_import_task.task_id。 */
    private String parentTaskId;

    /** 提交用户 ID。 */
    private Long userId;

    /** 目标题库 ID。 */
    private Long bankId;

    /** 待解答题目列表（仅 SINGLE/MULTI/JUDGE 的 MISSING 题）。 */
    private List<QuestionPreviewVO> questions;

    /** 提交时间戳（epoch 毫秒）。 */
    private Long submittedAt;
}
