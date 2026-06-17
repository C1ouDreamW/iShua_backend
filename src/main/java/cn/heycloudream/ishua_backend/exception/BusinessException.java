package cn.heycloudream.ishua_backend.exception;

import lombok.Getter;

/**
 * 可预期的业务异常，由全局处理器转换为 {@link cn.heycloudream.ishua_backend.common.vo.Result}。
 *
 * @author C1ouD
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
