package com.ecommerce.agent.service;

import com.ecommerce.agent.config.AgentExecutionProperties;
import com.ecommerce.agent.config.AgentExecutorConfig;
import com.ecommerce.agent.concurrency.AgentConcurrencyGuard;
import com.ecommerce.agent.metrics.AgentMetrics;
import com.ecommerce.common.exception.BizException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentTaskRunnerTest {

    @Test
    void returnsAgentResultAndReleasesGuard() {
        AgentExecutionProperties properties = new AgentExecutionProperties();
        properties.setGlobalConcurrency(2);
        properties.setChatQueueCapacity(2);
        properties.setChatTimeoutSeconds(2);
        ThreadPoolTaskExecutor executor = new AgentExecutorConfig().agentChatExecutor(
                properties,
                new SimpleMeterRegistry());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AgentConcurrencyGuard guard = mock(AgentConcurrencyGuard.class);
        AgentTaskRunner runner = new AgentTaskRunner(
                guard,
                executor,
                scheduler,
                properties,
                new AgentMetrics(new SimpleMeterRegistry()));

        try {
            String result = runner.run(1L, "test", () -> "ok");
            assertEquals("ok", result);
            verify(guard).acquire(1L);
            verify(guard).release(1L);
        } finally {
            executor.shutdown();
            scheduler.shutdownNow();
        }
    }

    @Test
    void wrapsCallableFailureAndReleasesGuard() {
        AgentExecutionProperties properties = new AgentExecutionProperties();
        properties.setGlobalConcurrency(2);
        properties.setChatQueueCapacity(2);
        properties.setChatTimeoutSeconds(2);
        ThreadPoolTaskExecutor executor = new AgentExecutorConfig().agentChatExecutor(
                properties,
                new SimpleMeterRegistry());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AgentConcurrencyGuard guard = mock(AgentConcurrencyGuard.class);
        AgentTaskRunner runner = new AgentTaskRunner(
                guard,
                executor,
                scheduler,
                properties,
                new AgentMetrics(new SimpleMeterRegistry()));

        try {
            assertThrows(IllegalStateException.class, () ->
                    runner.run(2L, "test", () -> {
                        throw new IllegalStateException("boom");
                    }));
            verify(guard).release(2L);
        } finally {
            executor.shutdown();
            scheduler.shutdownNow();
        }
    }
}
