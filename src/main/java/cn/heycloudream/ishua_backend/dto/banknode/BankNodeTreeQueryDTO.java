package cn.heycloudream.ishua_backend.dto.banknode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题库树扁平列表查询参数。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "题库树查询参数")
public class BankNodeTreeQueryDTO {

    @NotBlank(message = "scope 不能为空")
    @Pattern(regexp = "mine|public", flags = Pattern.Flag.CASE_INSENSITIVE, message = "scope 须为 mine 或 public")
    @Schema(description = "查询范围：mine-我的树，public-公开可见树", example = "public", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scope;

    @Schema(description = "子树根节点 ID，为空则返回整棵森林", example = "1")
    private Long rootId;
}
