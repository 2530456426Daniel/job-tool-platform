package com.jobtool.platform.support.devtool;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 工具：统一使用 Jackson（Spring Boot 自带的 JSON 库），不引入其它 JSON 依赖。
 *
 * <p>只用于读取人员名单里的 JSON 数组、以及导出失败时写出 JSON 错误体。</p>
 *
 * @author wangpengfei
 */
public final class JsonSupport {

    private static final ObjectMapper MAPPER = new JsonMapper();

    private JsonSupport() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 序列化为 JSON 字符串；失败属于编码问题，直接抛运行时异常
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }
}
