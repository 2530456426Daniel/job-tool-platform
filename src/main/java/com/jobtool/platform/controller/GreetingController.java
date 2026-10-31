package com.jobtool.platform.controller;

import com.jobtool.platform.common.ApiResponse;
import com.jobtool.platform.dto.GreetingRequest;
import com.jobtool.platform.entity.Greeting;
import com.jobtool.platform.service.GreetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接口层：Hello World 示例接口。
 *
 * <p>项目约定：<b>所有接口统一使用 POST</b>，参数通过请求体（JSON）传递，
 * 不使用 GET / PUT / DELETE 等其它请求方式。
 *
 * <p>实际访问路径为 {@code /api/hello}（context-path 为 /api）。
 */
@Tag(name = "Hello World", description = "前后端联调示例接口")
@RestController
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    /**
     * 打招呼。
     *
     * <p>请求体可为空对象 {@code {}}；{@code name} 省略或为空白时返回 {@code Hello, World!}。
     */
    @Operation(summary = "打招呼", description = "POST，请求体 { \"name\": \"Job Tool\" }；不传 name 时返回 Hello, World!")
    @PostMapping("/hello")
    public ApiResponse<Greeting> hello(@Valid @RequestBody(required = false) GreetingRequest request) {
        String name = request == null ? null : request.name();
        return ApiResponse.ok(greetingService.greet(name));
    }
}
