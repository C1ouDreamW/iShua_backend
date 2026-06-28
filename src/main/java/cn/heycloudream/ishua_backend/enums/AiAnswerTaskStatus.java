package cn.heycloudream.ishua_backend.enums;

import java.util.Arrays;

/**
 * AI 解答任务生命周期状态。
 *
 * @author C1ouD
 */
public enum AiAnswerTaskStatus {

    /** Java API 已创建任务并投递 Stream，Worker 尚未认领。 */
    SUBMITTED,

    /** Worker 正在分片 + 投票调用 LLM。 */
    PROCESSING,

    /** 全部题目解答成功，等待用户确认入库。 */
    ANSWERED,

    /** 部分题目失败，已成功部分可入库。 */
    PARTIAL,

    /** 整体失败或任务超时。 */
    FAILED,

    /** 用户已确认入库（走现有批量入库接口后回写）。 */
    IMPORTED;

    /**
     * 是否为终态（后续不再变化）。
     */
    public boolean isTerminal() {
        return this == IMPORTED || this == FAILED;
    }

    /**
     * 是否允许过渡到目标状态。
     */
    public boolean canTransitionTo(AiAnswerTaskStatus target) {
        return switch (this) {
            case SUBMITTED -> target == PROCESSING || target == ANSWERED || target == PARTIAL || target == FAILED;
            case PROCESSING -> target == ANSWERED || target == PARTIAL || target == FAILED;
            case ANSWERED, PARTIAL -> target == IMPORTED || target == FAILED;
            case IMPORTED, FAILED -> false;
        };
    }

    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(v -> v.name().equals(code.trim()));
    }
}
