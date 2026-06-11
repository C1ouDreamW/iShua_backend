package cn.heycloudream.ishua_backend.exception;

import lombok.Getter;

/**
 * 请求频率超限异常，由限流器抛出，全局处理器映射为 HTTP 429。
 *
 * @author C1ouD
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final int code = 429;

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
