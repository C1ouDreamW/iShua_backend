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

    /**
     * JSON 请求体格式非法或反序列化失败（多余逗号、类型不匹配等）。
     * 纯客户端错误，WARN 级别即可。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(400, "请求体格式错误或内容非法");
    }

    /**
     * HTTP 方法不匹配（例如 GET 请求仅支持 POST 的端点）。
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleHttpRequestMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        log.warn("HTTP 方法不支持 method={} supported={}", e.getMethod(), e.getSupportedHttpMethods());
        return Result.fail(405, "请求方法不允许，支持：" + e.getSupportedHttpMethods());
    }

    /**
     * 路径参数或查询参数类型转换失败（例如 /questions/abc 期望 Long）。
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        log.warn("参数类型不匹配 name={} value={} requiredType={}",
                e.getName(), e.getValue(),
                requiredType != null ? requiredType.getSimpleName() : "unknown");
        String hint = requiredType != null
                ? "参数 '" + e.getName() + "' 须为 " + requiredType.getSimpleName() + " 类型"
                : "参数 '" + e.getName() + "' 类型非法";
        return Result.fail(400, hint);
    }

    /**
     * Content-Type 不匹配（例如对 multipart/form-data 端点发送 application/json）。
     */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public Result<Void> handleHttpMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException e) {
        log.warn("Content-Type 不支持 contentType={} supported={}",
                e.getContentType(), e.getSupportedMediaTypes());
        return Result.fail(415, "不支持的 Content-Type，支持：" + e.getSupportedMediaTypes());
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
