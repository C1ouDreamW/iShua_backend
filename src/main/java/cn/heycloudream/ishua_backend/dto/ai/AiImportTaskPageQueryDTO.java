package cn.heycloudream.ishua_backend.dto.ai;

import cn.heycloudream.ishua_backend.common.dto.PageRequestDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 导入任务分页查询参数。
 *
 * @author C1ouD
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = """
        AI 导入任务分页查询参数（Query，配合 @ParameterObject 展开为 current、pageSize 等扁平参数）。
        仅返回当前登录用户提交的任务，按 submittedAt 倒序。
        """)
public class AiImportTaskPageQueryDTO extends PageRequestDTO {

    @Schema(description = "目标题库 ID；不传则返回用户全部题库下的任务", example = "1001")
    private Long bankId;

    @Schema(
            description = """
                    任务状态筛选，逗号分隔多值。合法值：SUBMITTED、PROCESSING、PARSED、IMPORTING、IMPORTED、FAILED、EXPIRED。
                    不传则不过滤状态。前端恢复场景常用：PARSED,PROCESSING,SUBMITTED
                    """,
            example = "PARSED,PROCESSING")
    private String status;

    @Schema(
            description = """
                    是否在列表项中携带预览题目（questions[]）。默认 false。
                    仅对 status=PARSED 的记录填充；响应体较大，建议 pageSize≤10。
                    """,
            example = "false")
    private Boolean includePreview;
}
