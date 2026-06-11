package cn.heycloudream.ishua_backend.dto.banknode;

import cn.heycloudream.ishua_backend.common.dto.PageRequestDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 根节点分页查询参数。
 *
 * @author C1ouD
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "题库树根节点分页查询")
public class BankNodeRootsQueryDTO extends PageRequestDTO {

    @NotBlank(message = "scope 不能为空")
    @Pattern(regexp = "mine|public", flags = Pattern.Flag.CASE_INSENSITIVE, message = "scope 须为 mine 或 public")
    @Schema(description = "查询范围：mine-我的根节点，public-公开根节点", example = "public", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scope;
}
