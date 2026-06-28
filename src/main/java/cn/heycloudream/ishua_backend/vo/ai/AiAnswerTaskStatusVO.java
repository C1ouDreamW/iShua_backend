package cn.heycloudream.ishua_backend.vo.ai;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 解答任务状态快照。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 解答任务状态快照")
public class AiAnswerTaskStatusVO {

    @Schema(description = "解答任务 ID（UUID）")
    private String answerTaskId;

    @Schema(
            description = """
                    任务状态：SUBMITTED（已入队）→ PROCESSING（解答中）→
                    ANSWERED（全部成功）/ PARTIAL（部分失败）/ FAILED（整体失败）→ IMPORTED（已入库）
                    """,
            example = "ANSWERED")
    private String status;

    @Schema(description = "FAILED/PARTIAL 时的错误摘要，或业务说明")
    private String message;

    @Schema(description = "待解答题目总数", example = "20")
    private Integer totalCount;

    @Schema(description = "成功解答数", example = "18")
    private Integer answeredCount;

    @ArraySchema(
            arraySchema = @Schema(description = """
                    解答结果列表。status=ANSWERED/PARTIAL 时返回。
                    每项为 QuestionPreviewVO，含 answerSource=AI_GENERATED 与 answerConfidence。
                    """),
            schema = @Schema(implementation = QuestionPreviewVO.class))
    private List<QuestionPreviewVO> questions;

    @Schema(description = "Worker 实测耗时与调用次数")
    private AiAnswerMetricsVO metrics;
}
