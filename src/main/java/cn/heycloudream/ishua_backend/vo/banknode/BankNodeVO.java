package cn.heycloudream.ishua_backend.vo.banknode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 题库树节点视图对象。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "题库树节点")
public class BankNodeVO {

    @Schema(description = "节点 ID")
    private Long id;

    @Schema(description = "所有者用户 ID")
    private Long userId;

    @Schema(description = "父节点 ID，根节点为 null")
    private Long parentId;

    @Schema(description = "节点类型：FOLDER | LEAF")
    private String nodeKind;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否公开：0-否，1-是")
    private Integer isPublic;

    @Schema(description = "同级排序号")
    private Integer sortNo;

    @Schema(description = "题目数量（LEAF 冗余字段）")
    private Integer questionCount;

    @Schema(description = "直接子节点数量")
    private Integer childCount;

    @Schema(description = "子树内 LEAF 节点总数")
    private Integer descendantLeafCount;

    @Schema(description = "子树是否含公开 LEAF")
    private Boolean hasPublicDescendant;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
