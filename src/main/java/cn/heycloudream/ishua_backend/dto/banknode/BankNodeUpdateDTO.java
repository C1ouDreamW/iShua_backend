package cn.heycloudream.ishua_backend.dto.banknode;

import cn.heycloudream.ishua_backend.common.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新题库树节点请求体。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新题库树节点请求")
public class BankNodeUpdateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = ValidationConstants.QUESTION_BANK_TITLE_MAX, message = "标题过长")
    @Schema(description = "节点标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Size(max = ValidationConstants.QUESTION_BANK_DESCRIPTION_MAX, message = "描述过长")
    @Schema(description = "节点描述")
    private String description;

    @Min(value = 0, message = "是否公开取值非法")
    @Max(value = 1, message = "是否公开取值非法")
    @Schema(description = "是否公开：0-否，1-是（LEAF 有效）")
    private Integer isPublic;

    @Schema(description = "同级排序号")
    private Integer sortNo;
}
