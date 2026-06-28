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
 * 试题表实体，对应数据库表 {@code question}。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("question")
public class Question {

    /**
     * 主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属题库 ID。
     */
    private Long questionBankId;

    /**
     * 题型（如 SINGLE、MULTI、JUDGE 等）。
     */
    private String questionType;

    /**
     * 题干（纯文本，可含换行）。
     */
    private String stem;

    /**
     * 选项等结构化数据，对应 MySQL JSON 列，持久化为 JSON 字符串。
     */
    private String optionsJson;

    /**
     * 标准答案（单选、多选等），对应 MySQL JSON 列，持久化为 JSON 字符串。
     */
    private String answerJson;

    /**
     * 答案来源：ORIGINAL（原文）/ MISSING（原文无答案）/ AI_GENERATED（AI 解答）。
     */
    private String answerSource;

    /**
     * 答案置信度：HIGH/MEDIUM/LOW。仅 AI 解答写入，否则为 null。
     */
    private String answerConfidence;

    /**
     * 解析。
     */
    private String analysis;

    /**
     * 大模型原始或清洗后的完整 JSON 备份（可选）。
     */
    private String rawLlmJson;

    /**
     * 题库内排序号。
     */
    private Integer sortNo;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除：0-否，1-是。
     */
    @TableLogic
    private Integer isDeleted;
}
