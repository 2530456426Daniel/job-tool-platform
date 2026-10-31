package com.jobtool.platform.service;

import com.jobtool.platform.entity.Greeting;

/**
 * 业务层接口：问候服务（Hello World 示例）。
 */
public interface GreetingService {

    /**
     * 生成问候信息。
     *
     * @param name 称呼，为空时使用默认称呼
     * @return 问候信息
     */
    Greeting greet(String name);
}
