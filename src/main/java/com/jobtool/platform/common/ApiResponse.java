package com.jobtool.platform.common;

/**
 * 统一接口响应结构体，前后端约定：业务数据统一从 {@code data} 中取。
 *
 * @param code    业务状态码，0 表示成功
 * @param message 提示信息
 * @param data    业务数据
 * @param <T>     业务数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_FAILURE = 1;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(CODE_SUCCESS, "success", data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(CODE_FAILURE, message, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
