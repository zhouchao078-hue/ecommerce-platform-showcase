package com.ecommerce.agent.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated executors prevent chat traffic, ticket streaming, and future
 * background workloads from competing for the same thread pool.
 */
@Configuration
public class AgentExecutorConfig {

    @Bean(name = "agentChatExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor agentChatExecutor(AgentExecutionProperties properties,
                                                    MeterRegistry meterRegistry) {
        int maxConcurrency = Math.max(1, properties.getGlobalConcurrency());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, maxConcurrency / 2));
        executor.setMaxPoolSize(maxConcurrency);
        executor.setQueueCapacity(Math.max(1, properties.getChatQueueCapacity()));
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("agent-chat-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        Gauge.builder("agent.execution.active",
                        executor,
                        value -> value.getThreadPoolExecutor().getActiveCount())
                .description("Active agent chat tasks")
                .register(meterRegistry);
        Gauge.builder("agent.execution.queue.size",
                        executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .description("Queued agent chat tasks")
                .register(meterRegistry);
        return executor;
    }

    @Bean(name = "ticketSseScheduler", destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor ticketSseScheduler(AgentExecutionProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                Math.max(1, properties.getSseSchedulerPoolSize()),
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "ticket-agent-sse-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    @Bean(name = "agentTimeoutScheduler", destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor agentTimeoutScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                runnable -> {
                    Thread thread = new Thread(runnable, "agent-timeout");
                    thread.setDaemon(true);
                    return thread;
                });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    @Bean(name = "taskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
        return scheduler;
    }
}
