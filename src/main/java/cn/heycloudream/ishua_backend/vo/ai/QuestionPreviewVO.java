package cn.heycloudream.ishua_backend.vo.ai;

import cn.heycloudream.ishua_backend.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预览题目 VO，供前端渲染导入确认页。
 * <p>
 * 与正式落库的 {@code Question} 实体分离，方便前端增删改后再提交。
 * </p>
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = """
        AI 预览/批量确认题目（确认导入前可编辑）。
        与 QuestionVO 不同：此处 options、answer 为数组；入库后读接口为 optionsJson、answerJson 字符串。
        """)
public class QuestionPreviewVO {

    @Schema(description = "题型", implementation = QuestionType.class, example = "SINGLE")
    private String questionType;

    @Schema(description = "题干文本", example = "理想气体状态方程 PV=nRT 中，R 的数值是？")
    private String stem;

    @ArraySchema(
            arraySchema = @Schema(description = "选项文案列表（判断题固定 [\"正确\",\"错误\"]）"),
            schema = @Schema(type = "string", example = "选项A"))
    private List<String> options;

    @ArraySchema(
            arraySchema = @Schema(description = "正确答案：单选 [\"B\"]；多选 [\"A\",\"C\"]；判断题 [\"T\"] 或 [\"F\"]"),
            schema = @Schema(type = "string", example = "C"))
    private List<String> answer;

    @Schema(description = "题目解析")
    private String analysis;

    /**
     * 答案来源：ORIGINAL（原文有答案）/ MISSING（原文无答案）/ AI_GENERATED（AI 解答生成）。
     * <p>
     * 缺省视为 ORIGINAL，兼容历史 LLM 输出与旧版 preview_json。
     */
    @Schema(description = "答案来源：ORIGINAL/MISSING/AI_GENERATED", example = "ORIGINAL")
    private String answerSource;

    /**
     * 答案置信度：HIGH/MEDIUM/LOW。仅 AI 解答流程写入，阶段 1 始终为 null。
     */
    @Schema(description = "答案置信度：HIGH/MEDIUM/LOW（仅 AI 解答写入）")
    private String answerConfidence;
}
