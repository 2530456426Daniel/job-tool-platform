package com.jobtool.platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hello World 接口测试：启动真实内嵌服务器，通过 HTTP 调用接口。
 *
 * <p>校验的是「前端实际会拿到什么」——POST 映射、请求体解析、参数校验、
 * 统一响应结构与 JSON 序列化。
 *
 * <p>说明：Boot 4 不再自带 MockMvc / TestRestTemplate，这里用 JDK 自带的 HttpClient，
 * 不引入额外依赖。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GreetingControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 与 GreetingRequest 上的 @Size 保持一致 */
    private static final int MAX_NAME_LENGTH = 32;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void emptyBodyShouldReturnDefaultGreeting() throws Exception {
        HttpResult result = post("/api/hello", "{}");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().path("code").asInt()).isZero();
        assertThat(result.body().path("message").asText()).isEqualTo("success");
        assertThat(result.body().path("data").path("name").asText()).isEqualTo("World");
        assertThat(result.body().path("data").path("message").asText()).isEqualTo("Hello, World!");
        assertThat(result.body().path("data").path("serverTime").asText()).isNotBlank();
    }

    @Test
    void absentBodyShouldAlsoReturnDefaultGreeting() throws Exception {
        HttpResult result = post("/api/hello", null);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().path("code").asInt()).isZero();
        assertThat(result.body().path("data").path("name").asText()).isEqualTo("World");
    }

    @Test
    void nameShouldBeUsedWhenProvided() throws Exception {
        HttpResult result = post("/api/hello", "{\"name\":\"Job Tool\"}");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().path("data").path("name").asText()).isEqualTo("Job Tool");
        assertThat(result.body().path("data").path("message").asText()).isEqualTo("Hello, Job Tool!");
    }

    @Test
    void blankNameShouldFallbackToDefault() throws Exception {
        HttpResult result = post("/api/hello", "{\"name\":\"   \"}");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().path("data").path("name").asText()).isEqualTo("World");
    }

    @Test
    void tooLongNameShouldBeRejectedByValidation() throws Exception {
        String tooLong = "a".repeat(MAX_NAME_LENGTH + 1);
        HttpResult result = post("/api/hello", OBJECT_MAPPER.writeValueAsString(new NameBody(tooLong)));

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().path("code").asInt()).isEqualTo(400);
        assertThat(result.body().path("message").asText()).contains("32");
    }

    @Test
    void greetingInterfaceShouldNotAcceptGet() throws Exception {
        // 项目约定所有接口统一 POST，GET 应返回 405
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/hello"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(OBJECT_MAPPER.readTree(response.body()).path("code").asInt()).isEqualTo(405);
    }

    /** 发起 POST 请求并返回状态码与响应体；body 为 null 表示不发送请求体 */
    private HttpResult post(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");

        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }

        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return new HttpResult(response.statusCode(), OBJECT_MAPPER.readTree(response.body()));
    }

    private record HttpResult(int status, JsonNode body) {
    }

    private record NameBody(String name) {
    }
}
