package cn.heycloudream.ishua_backend.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 解答流水线指标。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 解答 Worker 实测耗时与调用次数")
public class AiAnswerMetricsVO {

    @Schema(description = "LLM 总耗时（毫秒）", example = "45000")
    private Integer llmMs;

    @Schema(description = "总 LLM 调用次数（分片×投票）", example = "6")
    private Integer totalCalls;
}
