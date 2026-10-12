package com.ecommerce.agent.llm;

import com.ecommerce.agent.config.AgentExecutionProperties;
import com.ecommerce.agent.metrics.AgentMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Circuit-breaks external LLM calls and records provider metrics.
 */
@Component
public class LlmCallGuard {

    private final CircuitBreaker supervisorBreaker;
    private final CircuitBreaker ollamaBreaker;
    private final AgentMetrics metrics;

    public LlmCallGuard(AgentExecutionProperties properties, AgentMetrics metrics) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getLlmCircuitFailureRateThreshold())
                .slidingWindowSize(Math.max(2, properties.getLlmCircuitSlidingWindowSize()))
                .minimumNumberOfCalls(Math.max(1, properties.getLlmCircuitMinimumCalls()))
                .waitDurationInOpenState(
                        Duration.ofSeconds(Math.max(1L, properties.getLlmCircuitOpenSeconds())))
                .permittedNumberOfCallsInHalfOpenState(
                        Math.max(1, properties.getLlmCircuitHalfOpenCalls()))
                .recordExceptions(RuntimeException.class)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        this.supervisorBreaker = registry.circuitBreaker("supervisor-llm");
        this.ollamaBreaker = registry.circuitBreaker("ollama-llm");
        this.metrics = metrics;
    }

    public <T> T execute(String provider, Supplier<T> supplier) {
        long started = System.nanoTime();
        try {
            T result = CircuitBreaker.decorateSupplier(breaker(provider), supplier).get();
            metrics.recordLlmCall(provider, "success", System.nanoTime() - started);
            return result;
        } catch (RuntimeException ex) {
            metrics.recordLlmCall(provider, "failure", System.nanoTime() - started);
            throw ex;
        }
    }

    private CircuitBreaker breaker(String provider) {
        return "ollama".equalsIgnoreCase(provider) ? ollamaBreaker : supervisorBreaker;
    }
}
