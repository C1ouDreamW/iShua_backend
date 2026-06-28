package cn.heycloudream.ishua_backend.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 解答任务提交响应（阶段 2）。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 解答任务提交响应")
public class AiAnswerSubmitVO {

    @Schema(description = "解答任务 ID（UUID）", example = "a1b2c3d4e5f67890abcdef1234567890")
    private String answerTaskId;

    @Schema(description = "关联的 ai_import_task.task_id")
    private String parentTaskId;

    @Schema(description = "初始状态", example = "SUBMITTED")
    private String status;

    @Schema(description = "待解答题目数", example = "20")
    private Integer questionCount;
}
