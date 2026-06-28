package cn.heycloudream.ishua_backend.vo.question;

import cn.heycloudream.ishua_backend.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 试题对外展示数据（列表与详情共用），不包含大模型原始 JSON 等内部字段。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "试题信息")
public class QuestionVO {

    @Schema(description = "试题主键", example = "50001")
    private Long id;

    @Schema(description = "所属题库 ID", example = "1001")
    private Long questionBankId;

    @Schema(description = "题型", implementation = QuestionType.class, example = "SINGLE")
    private String questionType;

    @Schema(description = "题干纯文本")
    private String stem;

    @Schema(
            description = "选项 JSON 数组字符串。单选/多选为选项文案列表；简答题（SHORT_ANSWER）为 []",
            example = "[\"8.31 J/(mol·K)\",\"8.31 kJ/(mol·K)\",\"0.0821 L·atm/(mol·K)\"]")
    private String optionsJson;

    @Schema(
            description = "答案 JSON 数组字符串。单选/多选为字母；判断题为 [\"T\"]/[\"F\"]；简答题为文本要点数组",
            example = "[\"C\"]")
    private String answerJson;

    @Schema(description = "答案来源：ORIGINAL/MISSING/AI_GENERATED", example = "ORIGINAL")
    private String answerSource;

    @Schema(description = "答案置信度：HIGH/MEDIUM/LOW（仅 AI 解答写入）")
    private String answerConfidence;

    @Schema(description = "题目解析")
    private String analysis;

    @Schema(description = "题库内排序号", example = "1")
    private Integer sortNo;

    @Schema(description = "创建时间", type = "string", format = "date-time", example = "2026-05-14T00:00:46")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", type = "string", format = "date-time", example = "2026-05-14T00:00:46")
    private LocalDateTime updateTime;
}
