package cn.heycloudream.ishua_backend.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 刷题热点场景 Redis Key 与占位符约定。
 *
 * @author C1ouD
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IShuaRedisCacheConstants {

    /** 题库不存在时的空值占位，防缓存穿透（短 TTL）。 */
    public static final String NULL_BANK_PLACEHOLDER = "NULL_BANK";

    /** 热点题库详情（题库 VO + 全量试题列表）缓存前缀。 */
    private static final String BANK_DETAIL_PREFIX = "smart_ishua:bank_detail:";

    /** 重建缓存时的互斥锁前缀。 */
    private static final String BANK_DETAIL_LOCK_PREFIX = "smart_ishua:bank_detail:lock:";

    /** 重建锁持有时间（秒），需大于单次 MySQL 查询耗时。 */
    public static final int BANK_DETAIL_LOCK_TTL_SECONDS = 25;

    /** 空值缓存 TTL（秒）：5 分钟。 */
    public static final int NULL_BANK_CACHE_TTL_SECONDS = 300;

    /** 正常缓存 TTL 下限（秒）：30 分钟。 */
    public static final int BANK_DETAIL_CACHE_TTL_MIN_SECONDS = 30 * 60;

    /** 正常缓存 TTL 上限（秒）：40 分钟（含上界）。 */
    public static final int BANK_DETAIL_CACHE_TTL_MAX_SECONDS = 40 * 60;

    /**
     * 热点题库详情主 Key：{@code smart_ishua:bank_detail:{bankId}}。
     */
    public static String bankDetailKey(long bankId) {
        return BANK_DETAIL_PREFIX + bankId;
    }

    /**
     * 热点题库详情重建互斥锁 Key。
     */
    public static String bankDetailLockKey(long bankId) {
        return BANK_DETAIL_LOCK_PREFIX + bankId;
    }

    // ==================== AI 导入任务系统 Key ====================

    /** Redis Stream 任务下发流。 */
    public static final String TASK_STREAM_KEY = "ishua:task:stream";

    /** Stream 消费组名称。 */
    public static final String TASK_STREAM_GROUP = "ishua-ai-workers";

    /** Stream 最大长度（条）。 */
    public static final long TASK_STREAM_MAX_LEN = 1000;

    /** 任务状态 Key 前缀：{@code ishua:task:status:{taskId}}。 */
    private static final String TASK_STATUS_PREFIX = "ishua:task:status:";

    /** 任务结果 Key 前缀：{@code ishua:task:result:{taskId}}。 */
    private static final String TASK_RESULT_PREFIX = "ishua:task:result:";

    /** 任务元数据 Key 前缀：{@code ishua:task:meta:{taskId}}。 */
    private static final String TASK_META_PREFIX = "ishua:task:meta:";

    /** 任务幂等落库锁 Key 前缀：{@code ishua:task:import_lock:{taskId}}。 */
    private static final String TASK_IMPORT_LOCK_PREFIX = "ishua:task:import_lock:";

    /** 任务状态 TTL（秒）：1 小时。 */
    public static final int TASK_STATUS_TTL_SECONDS = 3600;

    /** 任务结果 TTL（秒）：30 分钟，预览确认后尽快落库。 */
    public static final int TASK_RESULT_TTL_SECONDS = 1800;

    /** 任务元数据 TTL（秒）：1 小时。 */
    public static final int TASK_META_TTL_SECONDS = 3600;

    /** 幂等落库锁 TTL（秒）：5 分钟。 */
    public static final int TASK_IMPORT_LOCK_TTL_SECONDS = 300;

    /** 任务扫描锁 Key，供 Watchdog 使用。 */
    public static final String TASK_WATCHDOG_LOCK_KEY = "ishua:task:watchdog:lock";

    public static String taskStatusKey(String taskId) {
        return TASK_STATUS_PREFIX + taskId;
    }

    public static String taskResultKey(String taskId) {
        return TASK_RESULT_PREFIX + taskId;
    }

    public static String taskMetaKey(String taskId) {
        return TASK_META_PREFIX + taskId;
    }

    public static String taskImportLockKey(String taskId) {
        return TASK_IMPORT_LOCK_PREFIX + taskId;
    }

    // ==================== AI 解答任务系统 Key（阶段 2，物理隔离） ====================

    /** 解答任务 Redis Stream（独立于阶段 1）。 */
    public static final String ANSWER_STREAM_KEY = "ishua:answer:stream";

    /** 解答任务 Stream 消费组（独立进程）。 */
    public static final String ANSWER_STREAM_GROUP = "ishua-answer-workers";

    /** Stream 最大长度（条）。 */
    public static final long ANSWER_STREAM_MAX_LEN = 1000;

    /** 解答任务状态 Key 前缀：{@code ishua:answer:status:{answerTaskId}}。 */
    private static final String ANSWER_STATUS_PREFIX = "ishua:answer:status:";

    /** 解答任务结果 Key 前缀：{@code ishua:answer:result:{answerTaskId}}。 */
    private static final String ANSWER_RESULT_PREFIX = "ishua:answer:result:";

    /** 解答任务元数据 Key 前缀：{@code ishua:answer:meta:{answerTaskId}}。 */
    private static final String ANSWER_META_PREFIX = "ishua:answer:meta:";

    /** 解答任务入库幂等锁 Key 前缀：{@code ishua:answer:import_lock:{answerTaskId}}。 */
    private static final String ANSWER_IMPORT_LOCK_PREFIX = "ishua:answer:import_lock:";

    /** 解答任务状态 TTL（秒）：1 小时。 */
    public static final int ANSWER_STATUS_TTL_SECONDS = 3600;

    /** 解答任务结果 TTL（秒）：30 分钟。 */
    public static final int ANSWER_RESULT_TTL_SECONDS = 1800;

    /** 解答任务元数据 TTL（秒）：1 小时。 */
    public static final int ANSWER_META_TTL_SECONDS = 3600;

    /** 解答入库幂等锁 TTL（秒）：5 分钟。 */
    public static final int ANSWER_IMPORT_LOCK_TTL_SECONDS = 300;

    /** 解答任务 Watchdog 扫描锁。 */
    public static final String ANSWER_WATCHDOG_LOCK_KEY = "ishua:answer:watchdog:lock";

    public static String answerStatusKey(String answerTaskId) {
        return ANSWER_STATUS_PREFIX + answerTaskId;
    }

    public static String answerResultKey(String answerTaskId) {
        return ANSWER_RESULT_PREFIX + answerTaskId;
    }

    public static String answerMetaKey(String answerTaskId) {
        return ANSWER_META_PREFIX + answerTaskId;
    }

    public static String answerImportLockKey(String answerTaskId) {
        return ANSWER_IMPORT_LOCK_PREFIX + answerTaskId;
    }

    // ==================== 注册邮箱验证码 Key ====================

    private static final String REGISTER_EMAIL_CODE_PREFIX = "ishua:register:email_code:";
    private static final String REGISTER_EMAIL_CODE_COOLDOWN_PREFIX = "ishua:register:email_code:cooldown:";
    private static final String REGISTER_EMAIL_CODE_ATTEMPTS_PREFIX = "ishua:register:email_code:attempts:";

    public static final int REGISTER_EMAIL_CODE_TTL_SECONDS = 10 * 60;
    public static final int REGISTER_EMAIL_CODE_RESEND_COOLDOWN_SECONDS = 60;
    public static final int REGISTER_EMAIL_CODE_MAX_ATTEMPTS = 5;

    public static String registerEmailCodeKey(String normalizedEmail) {
        return REGISTER_EMAIL_CODE_PREFIX + normalizedEmail;
    }

    public static String registerEmailCodeCooldownKey(String normalizedEmail) {
        return REGISTER_EMAIL_CODE_COOLDOWN_PREFIX + normalizedEmail;
    }

    public static String registerEmailCodeAttemptsKey(String normalizedEmail) {
        return REGISTER_EMAIL_CODE_ATTEMPTS_PREFIX + normalizedEmail;
    }
}
