package cn.heycloudream.ishua_backend.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理端 AI 导入任务清理结果。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理端 AI 导入任务清理结果（POST /api/v1/admin/ai-import/tasks/cleanup 的 data）")
public class AdminAiImportCleanupResultVO {

    @Schema(description = "本次请求是否为预检（与请求体 dryRun 一致）", example = "true")
    private Boolean dryRun;

    @Schema(description = "符合清理条件（PARSED 且 parsed_at 早于阈值）的任务总数，可能大于 processedCount", example = "12")
    private Long matchedCount;

    @Schema(
            description = "实际处理条数：dryRun=true 时为 0；dryRun=false 时为成功标记 EXPIRED 的条数",
            example = "0")
    private Integer processedCount;

    @Schema(description = "样例 taskId 列表（最多 10 条），便于运维核对后再实清")
    private List<String> sampleTaskIds;

    @Schema(
            description = "结果说明，如「dryRun：未执行写操作」或「已清理 N 个长时间未确认任务」",
            example = "dryRun：未执行写操作")
    private String message;
}
