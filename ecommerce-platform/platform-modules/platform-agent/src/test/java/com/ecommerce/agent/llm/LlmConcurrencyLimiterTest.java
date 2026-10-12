package com.ecommerce.agent.llm;

import com.ecommerce.agent.config.AgentExecutionProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmConcurrencyLimiterTest {

    @Test
    void releasesPermitWhenCallCompletes() throws Exception {
        AgentExecutionProperties properties = new AgentExecutionProperties();
        properties.setLlmConcurrency(1);
        properties.setLlmAcquireTimeoutMillis(100);
        LlmConcurrencyLimiter limiter = new LlmConcurrencyLimiter(properties);

        try (LlmConcurrencyLimiter.Permit ignored = limiter.acquire()) {
            assertEquals("ok", "ok");
        }

        try (LlmConcurrencyLimiter.Permit ignored = limiter.acquire()) {
            assertEquals("ok", "ok");
        }
    }

    @Test
    void rejectsWhenPermitCannotBeAcquiredInTime() {
        AgentExecutionProperties properties = new AgentExecutionProperties();
        properties.setLlmConcurrency(1);
        properties.setLlmAcquireTimeoutMillis(100);
        LlmConcurrencyLimiter limiter = new LlmConcurrencyLimiter(properties);

        try (LlmConcurrencyLimiter.Permit ignored = limiter.acquire()) {
            IllegalStateException exception =
                    assertThrows(IllegalStateException.class, limiter::acquire);
            assertEquals("LLM concurrency limit reached", exception.getMessage());
        }
    }
}
