package cn.heycloudream.ishua_backend.dto.banknode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 移动题库树节点请求体。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "移动题库树节点请求")
public class BankNodeMoveDTO {

    @Schema(description = "新父节点 ID，NULL 表示移到根", example = "5")
    private Long newParentId;

    @Min(value = 0, message = "排序号不能为负")
    @Schema(description = "在新父节点下的排序号", example = "0")
    private Integer newSortNo;
}
