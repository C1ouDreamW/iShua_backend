package cn.heycloudream.ishua_backend.dto.banknode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题库树扁平列表查询参数（可选子树根节点）。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "题库树子树查询参数")
public class BankNodeSubtreeQueryDTO {

    @Schema(description = "子树根节点 ID，为空则返回整棵森林", example = "1")
    private Long rootId;
}
