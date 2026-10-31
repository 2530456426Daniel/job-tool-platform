package com.jobtool.platform.service.impl;

import com.jobtool.platform.entity.Greeting;
import com.jobtool.platform.service.GreetingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 业务层实现：问候服务。
 */
@Service
public class GreetingServiceImpl implements GreetingService {

    /** 默认称呼 */
    private static final String DEFAULT_NAME = "World";

    /** 称呼最大长度，超出则截断，避免异常长的输入 */
    private static final int MAX_NAME_LENGTH = 32;

    @Override
    public Greeting greet(String name) {
        String resolved = resolveName(name);
        return new Greeting(resolved, "Hello, %s!".formatted(resolved), LocalDateTime.now());
    }

    private String resolveName(String name) {
        if (!StringUtils.hasText(name)) {
            return DEFAULT_NAME;
        }
        String trimmed = name.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
