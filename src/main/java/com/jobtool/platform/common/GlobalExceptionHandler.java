package com.jobtool.platform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把异常统一转换为 {@link ApiResponse}，避免堆栈信息直接抛给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数不合法（业务校验失败） */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("参数校验失败: {}", ex.getMessage());
        return ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    /** @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * 请求方式不支持。
     *
     * <p>项目约定所有接口统一使用 POST，用其它方式访问时会走到这里，
     * 需要显式返回 405，否则会被下面的兜底处理成 500。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("请求方式不支持: {}", ex.getMessage());
        return ApiResponse.fail(HttpStatus.METHOD_NOT_ALLOWED.value(),
                "请求方式不支持，本项目接口统一使用 POST");
    }

    /** 接口不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NoResourceFoundException ex) {
        log.warn("接口不存在: {}", ex.getMessage());
        return ApiResponse.fail(HttpStatus.NOT_FOUND.value(), "接口不存在");
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("服务内部异常", ex);
        return ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务内部异常，请联系管理员");
    }
}
