package com.ecommerce.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 电商平台启动类
 *
 * @author ecommerce
 * @since 1.0.0
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.ecommerce")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
