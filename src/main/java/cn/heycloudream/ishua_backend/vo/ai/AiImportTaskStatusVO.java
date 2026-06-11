package cn.heycloudream.ishua_backend.vo.ai;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 导入任务状态快照（新任务体系）。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 导入任务状态快照")
public class AiImportTaskStatusVO {

    @Schema(description = "任务 ID（UUID）")
    private String taskId;

    @Schema(
            description = """
                    任务状态：SUBMITTED（已入队）→ PROCESSING（解析中）→ PARSED（可预览）→
                    IMPORTING（落库中，可选）→ IMPORTED（完成）、FAILED（失败）或 EXPIRED（长时间未确认已过期）
                    """,
            example = "PARSED")
    private String status;

    @Schema(description = "FAILED/EXPIRED 时的错误摘要，或业务说明；成功流转中常为空")
    private String message;

    @Schema(description = "解析出的题目总数；status=PARSED 时通常等于 questions.length", example = "42")
    private Integer totalCount;

    @ArraySchema(
            arraySchema = @Schema(description = """
                    预览题目列表。status=PARSED 时返回（优先 MySQL preview_json，其次 Redis result）。
                    终态 IMPORTED/FAILED/EXPIRED 时不返回或为空。
                    """),
            schema = @Schema(implementation = QuestionPreviewVO.class))
    private List<QuestionPreviewVO> questions;

    @Schema(description = "Worker 实测流水线耗时；PARSED/FAILED 同步时写入 MySQL，对外轮询一般不返回")
    private AiImportTaskPipelineMetricsVO metrics;
}
