package com.ecommerce.agentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Independent runtime for the agent and support bounded contexts.
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.ecommerce",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        "com\\.ecommerce\\.auth\\.controller\\..*",
                        "com\\.ecommerce\\.user\\.controller\\..*",
                        "com\\.ecommerce\\.merchant\\.controller\\..*",
                        "com\\.ecommerce\\.product\\.controller\\..*",
                        "com\\.ecommerce\\.product\\.seckill\\.controller\\..*",
                        "com\\.ecommerce\\.cart\\.controller\\..*",
                        "com\\.ecommerce\\.order\\.controller\\..*",
                        "com\\.ecommerce\\.feed\\.controller\\..*",
                        "com\\.ecommerce\\.review\\.controller\\..*"
                }
        )
)
@EnableScheduling
public class AgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentServiceApplication.class, args);
    }
}
