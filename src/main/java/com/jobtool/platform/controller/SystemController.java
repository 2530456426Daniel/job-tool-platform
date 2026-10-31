package com.jobtool.platform.controller;

import com.jobtool.platform.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 接口层：应用自身状态接口，供前端探活/联调使用。
 *
 * <p>项目约定：<b>所有接口统一使用 POST</b>。
 */
@Tag(name = "系统", description = "应用状态相关接口")
@RestController
@RequestMapping("/system")
public class SystemController {

    private final String applicationName;

    public SystemController(@Value("${spring.application.name:job-tool-platform}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Operation(summary = "应用信息", description = "POST，无请求体")
    @PostMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(Map.of(
                "application", applicationName,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
