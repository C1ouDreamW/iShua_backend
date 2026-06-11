package cn.heycloudream.ishua_backend.vo.ai;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 导入任务列表摘要。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 导入任务列表单条摘要（用于 GET /api/v1/ai-import/tasks 的 data.records[]）")
public class AiImportTaskSummaryVO {

    @Schema(description = "业务任务 ID（UUID），与轮询、batch 接口一致", example = "a1b2c3d4e5f67890abcdef1234567890")
    private String taskId;

    @Schema(description = "目标题库 ID", example = "1001")
    private Long bankId;

    @Schema(description = "用户上传时的原始文件名", example = "期末复习.pdf")
    private String fileName;

    @Schema(
            description = """
                    任务状态：SUBMITTED、PROCESSING、PARSED、IMPORTING、IMPORTED、FAILED、EXPIRED。
                    PARSED 表示可进入预览确认；EXPIRED 表示已过期清理，需重新 submit。
                    """,
            example = "PARSED")
    private String status;

    @Schema(description = "FAILED/EXPIRED 时的原因摘要，或成功态业务说明；可为空", example = "解析完成，待确认导入")
    private String message;

    @Schema(description = "解析出的题目数量；PARSED 及之后有意义", example = "42")
    private Integer questionCount;

    @Schema(description = "任务提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "进入 PARSED 的时间；未解析完成时为 null")
    private LocalDateTime parsedAt;

    @Schema(description = "确认 batch 落库完成时间；未导入时为 null")
    private LocalDateTime importedAt;

    @Schema(description = "被管理端清理为 EXPIRED 的时间；未过期时为 null")
    private LocalDateTime expiredAt;

    @ArraySchema(
            arraySchema = @Schema(description = "预览题目列表；仅查询参数 includePreview=true 且本条 status=PARSED 时非空"),
            schema = @Schema(implementation = QuestionPreviewVO.class))
    private List<QuestionPreviewVO> questions;
}
