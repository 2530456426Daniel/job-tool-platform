package com.jobtool.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载冒烟测试：确保 Spring 容器与数据源、MyBatis 能正常装配。
 */
@SpringBootTest
class JobToolPlatformApplicationTests {

    @Test
    void contextLoads() {
        // 上下文启动成功即通过
    }
}
