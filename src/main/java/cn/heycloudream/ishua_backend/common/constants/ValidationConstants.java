package cn.heycloudream.ishua_backend.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Global validation limits.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationConstants {

    public static final int QUESTION_BANK_TITLE_MAX = 200;
    public static final int QUESTION_BANK_DESCRIPTION_MAX = 2000;
    public static final int QUESTION_TYPE_MAX = 32;
    public static final int QUESTION_STEM_MAX = 8192;
    public static final int QUESTION_OPTIONS_JSON_MAX = 65535;
    public static final int QUESTION_ANSWER_JSON_MAX = 4096;
    public static final int QUESTION_ANALYSIS_MAX = 8192;
    public static final int KEYWORD_MAX = 200;
    public static final int PAGE_SIZE_MAX = 100;
    public static final long FILE_IMPORT_MAX_SIZE_BYTES = 10L * 1024 * 1024;

    public static final int AUTH_EMAIL_MAX = 254;
    public static final int AUTH_EMAIL_CODE_LENGTH = 6;
    public static final int AUTH_EMAIL_CODE_MAX_ATTEMPTS = 5;
    public static final int AUTH_EMAIL_CODE_TTL_SECONDS = 10 * 60;
    public static final int AUTH_EMAIL_CODE_RESEND_COOLDOWN_SECONDS = 60;

    public static final int TURNSTILE_TOKEN_MAX_LENGTH = 2048;
    public static final String TURNSTILE_ACTION_REGISTER_EMAIL_CODE = "register_email_code";
}
