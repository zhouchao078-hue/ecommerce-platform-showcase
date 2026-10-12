package com.ecommerce.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    /** 环境标识：dev=灰度mock, prod=真实LLM */
    private String env = "dev";
    private final Supervisor supervisor = new Supervisor();
    private final Commerce commerce = new Commerce();
    private final Local local = new Local();

    @Data
    public static class Supervisor {
        private String model = "deepseek-chat";
        private String apiKey = "";
        private String endpoint = "https://api.deepseek.com/v1/chat/completions";
    }

    @Data
    public static class Commerce {
        private String model = "gpt-4o";
        private String apiKey = "";
        private String endpoint = "https://api.openai.com/v1/chat/completions";
    }

    @Data
    public static class Local {
        private String model = "llama3.2";
        private String endpoint = "http://localhost:11434/api/chat";
    }

    /** 判断是否为灰度测试环境 */
    public boolean isDev() {
        return "dev".equalsIgnoreCase(env);
    }

    /** 判断是否为正式环境 */
    public boolean isProd() {
        return "prod".equalsIgnoreCase(env);
    }
}
