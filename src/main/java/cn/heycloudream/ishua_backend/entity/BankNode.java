package cn.heycloudream.ishua_backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 题库树节点实体，对应数据库表 {@code bank_node}。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("bank_node")
public class BankNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long parentId;

    /**
     * FOLDER | LEAF。
     */
    private String nodeKind;

    private String title;

    private String description;

    private Integer isPublic;

    private Integer sortNo;

    private Integer questionCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
