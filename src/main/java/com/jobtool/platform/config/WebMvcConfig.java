package com.jobtool.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：开发阶段允许前端开发服务器跨域访问。
 *
 * <p>前端开发服务器已配置代理（见 job-tool-web/vite.config.ts），走代理时不会触发跨域；
 * 这里的 CORS 配置用于前端直连后端的场景。
 *
 * <p>项目约定所有接口统一使用 POST，因此这里只放行 POST 与预检请求 OPTIONS。
 * 生产环境请收敛 allowedOriginPatterns。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                // 只允许 POST，以及浏览器跨域预检所需的 OPTIONS
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
