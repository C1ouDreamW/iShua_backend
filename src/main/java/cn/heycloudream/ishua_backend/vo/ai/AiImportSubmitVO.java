package cn.heycloudream.ishua_backend.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 导入提交响应。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 导入提交响应")
public class AiImportSubmitVO {

    @Schema(description = "任务 ID（UUID）", example = "a1b2c3d4e5f67890abcdef1234567890")
    private String taskId;

    @Schema(description = "初始状态", example = "SUBMITTED")
    private String status;
}
