package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway 启动类
 * <p>
 * Spring Cloud Gateway 作为整个电商平台的统一入口，
 * 将请求路由到 backend (platform-app, 端口 8081)。
 * 本身不做鉴权，纯路由转发。
 *
 * @author ecommerce
 * @since 1.0.0
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
