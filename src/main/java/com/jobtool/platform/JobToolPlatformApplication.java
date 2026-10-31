package com.jobtool.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Job Tool 后端服务启动类。
 *
 * <p>分层约定：
 * <ul>
 *   <li>{@code controller} —— 接口层，只负责请求/响应与参数校验</li>
 *   <li>{@code service} —— 业务层，承载业务逻辑与事务边界</li>
 *   <li>{@code mapper} —— 持久化层，负责数据库访问</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.jobtool.platform.mapper")
public class JobToolPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobToolPlatformApplication.class, args);
    }
}
