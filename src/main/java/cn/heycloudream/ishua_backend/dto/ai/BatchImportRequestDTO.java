package cn.heycloudream.ishua_backend.dto.ai;

import cn.heycloudream.ishua_backend.vo.ai.QuestionPreviewVO;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量确认导入请求：前端预览确认后提交。
 *
 * @author C1ouD
 */
@Data
@Schema(description = """
        批量确认导入请求（JSON）。须在任务 status=PARSED 时提交；服务端优先使用 DB/Redis 中的预览缓存落库。
        """)
public class BatchImportRequestDTO {

    @NotBlank(message = "任务 ID 不能为空")
    @Schema(
            description = "AI 导入任务 ID（UUID），来自 submit 响应、GET /ai-import/tasks 或 GET .../tasks/{taskId}/status",
            example = "a1b2c3d4e5f67890abcdef1234567890",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskId;

    @NotEmpty(message = "导入题目列表不能为空")
    @Valid
    @ArraySchema(
            arraySchema = @Schema(description = "经用户预览页确认/编辑后的题目列表；结构与轮询接口 QuestionPreviewVO 一致"),
            schema = @Schema(implementation = QuestionPreviewVO.class))
    private List<QuestionPreviewVO> questions;
}
