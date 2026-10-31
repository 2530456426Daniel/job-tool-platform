package com.jobtool.platform.entity;

import java.time.LocalDateTime;

/**
 * 问候信息，用于 Hello World 前后端联调。
 *
 * @param name      称呼
 * @param message   问候内容
 * @param serverTime 服务端时间（证明数据确实来自后端）
 */
public record Greeting(String name, String message, LocalDateTime serverTime) {
}
