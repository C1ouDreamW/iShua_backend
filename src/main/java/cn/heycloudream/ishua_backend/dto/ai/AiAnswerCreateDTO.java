package cn.heycloudream.ishua_backend.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * AI 解答任务创建请求。
 * <p>
 * 二选一：指定题目下标列表，或使用 filter=MISSING 自动筛选所有无答案客观题。
 * 同时指定时以 questionIndices 为准。
 * </p>
 *
 * @author C1ouD
 */
@Data
@Schema(description = "AI 解答任务创建请求")
public class AiAnswerCreateDTO {

    @Schema(
            description = """
                    指定待解答题目在 preview_json 中的下标列表（0-based）。
                    与 filter 二选一；同时给出时以此为准。
                    """,
            example = "[0, 3, 5]")
    private List<Integer> questionIndices;

    @Schema(
            description = """
                    过滤模式：MISSING 表示自动筛选所有 answerSource=MISSING 且题型为 SINGLE/MULTI/JUDGE 的题目。
                    与 questionIndices 二选一。
                    """,
            example = "MISSING")
    private String filter;
}
