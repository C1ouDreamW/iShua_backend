package cn.heycloudream.ishua_backend.exception;

import cn.heycloudream.ishua_backend.common.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理，将业务与参数校验错误统一为 {@link Result}。
 *
 * @author C1ouD
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        if (e.getCause() != null) {
            log.error("业务异常包含根因 code={}", e.getCode(), e);
        } else {
            log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 文件上传大小超限：由 Spring {@code MaxUploadSizeExceededException} 触发。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.fail(400, "上传文件过大，最大支持 10 MB");
    }

    /** 限流拦截：返回 429 Too Many Requests。 */
    @ExceptionHandler(RateLimitException.class)
    public Result<Void> handleRateLimit(RateLimitException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        return Result.fail(400, firstFieldErrorMessage(e.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        return Result.fail(400, firstFieldErrorMessage(e.getBindingResult()));
    }

    /** Redis / MySQL 等数据访问异常，对外统一返回 500。 */
    @ExceptionHandler(DataAccessException.class)
    public Result<Void> handleDataAccess(DataAccessException e) {
        log.error("数据访问异常", e);
        return Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务繁忙，请稍后重试");
    }

    /** 兜底：未预期的运行时异常，避免直接暴露 Tomcat 错误页。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务繁忙，请稍后重试");
    }

    private static String firstFieldErrorMessage(BindingResult br) {
        return br.getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("参数校验失败");
    }
}
