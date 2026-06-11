package cn.heycloudream.ishua_backend.enums;

import java.util.Arrays;

/**
 * AI 导入任务生命周期状态（5 态 + FAILED）。
 *
 * @author C1ouD
 */
public enum AiImportTaskStatus {

    /** 文件已接收、任务已入 Stream，Worker 尚未认领。 */
    SUBMITTED,

    /** Worker 已认领，正在调 LLM 解析。 */
    PROCESSING,

    /** 解析成功，结果已写入 Redis，等待前端预览确认。 */
    PARSED,

    /** 用户已确认导入，正在批量落库。 */
    IMPORTING,

    /** 落库完成，结果已清理。 */
    IMPORTED,

    /** 任意环节异常（含超时/取消）。 */
    FAILED,

    /** 解析成功后长时间未确认，已由管理端或清理策略过期。 */
    EXPIRED;

    /**
     * 是否为终态（后续不再变化）。
     */
    public boolean isTerminal() {
        return this == IMPORTED || this == FAILED || this == EXPIRED;
    }

    /**
     * 是否允许过渡到目标状态。
     */
    public boolean canTransitionTo(AiImportTaskStatus target) {
        return switch (this) {
            case SUBMITTED -> target == PROCESSING || target == FAILED;
            case PROCESSING -> target == PARSED || target == FAILED;
            case PARSED -> target == IMPORTING || target == IMPORTED || target == FAILED || target == EXPIRED;
            case IMPORTING -> target == IMPORTED || target == FAILED;
            case IMPORTED, FAILED, EXPIRED -> false;
        };
    }

    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(v -> v.name().equals(code.trim()));
    }
}
