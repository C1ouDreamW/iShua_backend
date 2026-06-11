package cn.heycloudream.ishua_backend.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 导入任务按状态聚合统计项。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 导入任务按 status 聚合的单项统计")
public class AdminAiImportStatusStatVO {

    @Schema(description = "任务状态", example = "IMPORTED")
    private String status;

    @Schema(description = "该状态任务数", example = "42")
    private Long count;

    @Schema(
            description = "该状态下任务的平均流水线耗时（秒，pipeline_duration_ms）；无实测数据时不参与计算",
            example = "18.5")
    private Double avgParseSeconds;
}
