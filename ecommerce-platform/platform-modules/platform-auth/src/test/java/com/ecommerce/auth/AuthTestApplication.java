package com.ecommerce.auth;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 启动类
 * 用于 auth 模块的集成测试，避免依赖 platform-app 模块
 */
@SpringBootApplication(scanBasePackages = "com.ecommerce")
public class AuthTestApplication {
}
