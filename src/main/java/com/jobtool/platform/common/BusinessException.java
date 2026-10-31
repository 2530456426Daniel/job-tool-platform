package com.jobtool.platform.common;

/**
 * 业务异常：可预期的、需要把提示语直接展示给用户的错误。
 *
 * <p>由 {@link GlobalExceptionHandler} 统一转换为
 * {@code {"code": 业务码, "message": 提示语, "data": null}}，HTTP 状态保持 200，
 * 这样前端能拿到准确的业务提示，而不是被 HTTP 4xx/5xx 的通用文案覆盖。</p>
 *
 * @author wangpengfei
 */
public class BusinessException extends RuntimeException {

    /** 业务码 */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResponseCode responseCode) {
        super(responseCode.getMessage());
        this.code = responseCode.getCode();
    }

    public BusinessException(ResponseCode responseCode, Throwable cause) {
        super(responseCode.getMessage(), cause);
        this.code = responseCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
