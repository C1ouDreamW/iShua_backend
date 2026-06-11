package cn.heycloudream.ishua_backend.vo.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 导入流水线耗时（Worker 侧 monotonic 计时，毫秒）。
 *
 * @author C1ouD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 导入 Worker 实测耗时（毫秒），经 Redis 同步至 MySQL")
public class AiImportTaskPipelineMetricsVO {

    @Schema(description = "MinerU 文档解析耗时（毫秒）", example = "120000")
    private Integer mineruMs;

    @Schema(description = "LLM 题目抽取耗时（毫秒）", example = "8500")
    private Integer llmMs;

    @Schema(description = "MinerU + LLM 总耗时（毫秒）", example = "128500")
    private Integer pipelineMs;
}
