package com.ecommerce.agent.llm;

import com.ecommerce.agent.config.AgentExecutionProperties;
import com.ecommerce.agent.metrics.AgentMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmCallGuardTest {

    @Test
    void opensCircuitAfterMinimumFailures() {
        AgentExecutionProperties properties = new AgentExecutionProperties();
        properties.setLlmCircuitFailureRateThreshold(50);
        properties.setLlmCircuitSlidingWindowSize(2);
        properties.setLlmCircuitMinimumCalls(2);
        properties.setLlmCircuitOpenSeconds(30);
        properties.setLlmCircuitHalfOpenCalls(1);
        LlmCallGuard guard = new LlmCallGuard(
                properties,
                new AgentMetrics(new SimpleMeterRegistry()));

        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalStateException.class, () ->
                    guard.execute("supervisor", () -> {
                        throw new IllegalStateException("provider unavailable");
                    }));
        }

        assertThrows(CallNotPermittedException.class, () ->
                guard.execute("supervisor", () -> "should not execute"));
    }
}
