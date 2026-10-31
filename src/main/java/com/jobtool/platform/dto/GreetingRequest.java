package com.jobtool.platform.dto;

import jakarta.validation.constraints.Size;

/**
 * Hello World 请求体。
 *
 * <p>接口统一使用 POST，参数通过请求体传递。请求体可以为空，
 * 此时 {@code name} 为 null，由业务层回退到默认称呼。
 *
 * @param name 称呼，可选
 */
public record GreetingRequest(
        @Size(max = 32, message = "称呼长度不能超过 32 个字符")
        String name) {
}
