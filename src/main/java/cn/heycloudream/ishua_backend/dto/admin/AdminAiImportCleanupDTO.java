package cn.heycloudream.ishua_backend.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理端清理长时间未确认 AI 导入任务的请求。
 *
 * @author C1ouD
 */
@Data
@Schema(description = """
        管理端 AI 导入任务清理请求（JSON）。仅匹配 status=PARSED 且 parsed_at 早于阈值的记录。
        生产环境建议先 dryRun=true 预检，确认 sampleTaskIds 后再 dryRun=false 实清。
        """)
public class AdminAiImportCleanupDTO {

    @Min(value = 1, message = "过期天数至少为 1 天")
    @Max(value = 365, message = "过期天数不能超过 365 天")
    @Schema(
            description = "parsed_at 早于「当前时间 − olderThanDays 天」的 PARSED 任务纳入清理范围；不传时服务端默认 7（见配置 ishua.ai-import.cleanup-default-older-than-days）",
            example = "7")
    private Integer olderThanDays;

    @Schema(description = "可选：仅清理指定题库下的任务", example = "1001")
    private Long bankId;

    @Schema(description = "可选：仅清理指定用户提交的任务", example = "1")
    private Long userId;

    @Schema(
            description = "是否仅预检：true 只返回 matchedCount 与 sampleTaskIds，不写库；false 执行 EXPIRED 标记与 Redis 清理。不传时视为 true",
            example = "true",
            defaultValue = "true")
    private Boolean dryRun;

    @Schema(
            description = "实清（dryRun=false）时是否删除 file_url 指向的上传文件；默认 false，仅清任务与缓存",
            example = "false",
            defaultValue = "false")
    private Boolean deleteFiles;

    @Min(value = 1, message = "单次处理数量至少为 1")
    @Max(value = 1000, message = "单次处理数量不能超过 1000")
    @Schema(description = "单次请求最多处理的任务条数，防止长事务；不传时默认 200", example = "200", defaultValue = "200")
    private Integer maxBatch;
}
