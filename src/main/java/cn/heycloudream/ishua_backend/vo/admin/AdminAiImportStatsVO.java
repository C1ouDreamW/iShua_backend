package cn.heycloudream.ishua_backend.vo.admin;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端 AI 导入任务统计看板数据。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理端 AI 导入任务统计看板（GET /api/v1/admin/ai-import/stats 的 data）")
public class AdminAiImportStatsVO {

    @Schema(description = "统计窗口天数（与请求参数 days 一致）", example = "30")
    private Integer periodDays;

    @Schema(description = "统计起始时间（含）：submitted_at >= periodStart")
    private LocalDateTime periodStart;

    @Schema(description = "统计截止时间（不含，通常为接口调用时刻）")
    private LocalDateTime periodEnd;

    @Schema(description = "窗口内提交的任务总数", example = "120")
    private Long totalTasks;

    @ArraySchema(
            arraySchema = @Schema(description = "各状态任务数及该状态下的平均解析耗时"),
            schema = @Schema(implementation = AdminAiImportStatusStatVO.class))
    private List<AdminAiImportStatusStatVO> statusStats;

    @Schema(description = "日均提交量（totalTasks / periodDays）", example = "4.0")
    private Double dailyAvgSubmitCount;

    @Schema(
            description = "Worker 实测流水线平均耗时（秒，MinerU + LLM）；无 pipeline_duration_ms 的历史任务不参与",
            example = "128.5")
    private Double avgPipelineSeconds;

    @Schema(description = "MinerU 平均耗时（秒）", example = "120.0")
    private Double avgMineruSeconds;

    @Schema(description = "LLM 平均耗时（秒）", example = "8.5")
    private Double avgLlmSeconds;

    @Schema(
            description = "同 avgPipelineSeconds，保留兼容",
            example = "128.5")
    private Double avgParseSeconds;

    @Schema(description = "平均解析题目数（question_count 非空记录的平均值）", example = "15.6")
    private Double avgQuestionCount;

    @Schema(
            description = "失败率（status=FAILED 占 totalTasks 的比例，0~1，如 0.08 表示 8%）",
            example = "0.08")
    private Double failureRate;
}
