package com.ecommerce.agent.service;

import com.ecommerce.agent.config.AgentExecutionProperties;
import com.ecommerce.agent.concurrency.AgentConcurrencyGuard;
import com.ecommerce.agent.metrics.AgentMetrics;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared non-blocking execution boundary for all interactive agents.
 */
@Component
public class AgentTaskRunner {

    private final AgentConcurrencyGuard concurrencyGuard;
    private final ThreadPoolTaskExecutor chatExecutor;
    private final ScheduledExecutorService timeoutScheduler;
    private final AgentExecutionProperties executionProperties;
    private final AgentMetrics metrics;

    public AgentTaskRunner(AgentConcurrencyGuard concurrencyGuard,
                           @Qualifier("agentChatExecutor") ThreadPoolTaskExecutor chatExecutor,
                           @Qualifier("agentTimeoutScheduler") ScheduledExecutorService timeoutScheduler,
                           AgentExecutionProperties executionProperties,
                           AgentMetrics metrics) {
        this.concurrencyGuard = concurrencyGuard;
        this.chatExecutor = chatExecutor;
        this.timeoutScheduler = timeoutScheduler;
        this.executionProperties = executionProperties;
        this.metrics = metrics;
    }

    public <T> CompletableFuture<T> submit(Long userId, String operation, Callable<T> callable) {
        Timer.Sample executionTimer = metrics.startTimer();
        AtomicBoolean metricRecorded = new AtomicBoolean(false);
        try {
            concurrencyGuard.acquire(userId);
        } catch (RuntimeException ex) {
            metrics.recordAdmission(operation, "limit_rejected");
            recordTimerOnce(executionTimer, metricRecorded, operation, "rejected");
            CompletableFuture<T> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(ex);
            return rejected;
        }
        metrics.recordAdmission(operation, "accepted");

        FutureTask<T> task = new FutureTask<>(callable);
        CompletableFuture<T> result = new CompletableFuture<>();
        ScheduledFuture<?> timeout = timeoutScheduler.schedule(
                () -> {
                    if (result.completeExceptionally(new BizException(
                            ErrorCode.TOO_MANY_REQUESTS,
                            operation + " timed out"))) {
                        task.cancel(true);
                    }
                },
                Math.max(1, executionProperties.getChatTimeoutSeconds()),
                TimeUnit.SECONDS);

        result.whenComplete((value, failure) -> {
            if (result.isCancelled()) {
                task.cancel(true);
            }
            timeout.cancel(false);
            recordTimerOnce(
                    executionTimer,
                    metricRecorded,
                    operation,
                    resultLabel(failure));
        });

        try {
            chatExecutor.execute(() -> {
                T value = null;
                Throwable failure = null;
                boolean cancelled = false;
                try {
                    task.run();
                    value = task.get();
                } catch (CancellationException ex) {
                    cancelled = true;
                } catch (ExecutionException ex) {
                    failure = unwrap(ex.getCause(), operation);
                } catch (Throwable ex) {
                    failure = unwrap(ex, operation);
                } finally {
                    timeout.cancel(false);
                    concurrencyGuard.release(userId);
                }
                if (cancelled) {
                    result.cancel(false);
                } else if (failure != null) {
                    result.completeExceptionally(failure);
                } else {
                    result.complete(value);
                }
            });
        } catch (TaskRejectedException ex) {
            timeout.cancel(false);
            concurrencyGuard.release(userId);
            metrics.recordAdmission(operation, "queue_rejected");
            result.completeExceptionally(new BizException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    operation + " queue is full"));
        }
        return result;
    }

    public <T> T run(Long userId, String operation, Callable<T> callable) {
        try {
            return submit(userId, operation, callable).join();
        } catch (CancellationException ex) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, operation + " was cancelled");
        } catch (java.util.concurrent.CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BizException(
                    ErrorCode.INTERNAL_ERROR,
                    cause == null ? operation + " failed" : cause.getMessage());
        }
    }

    private RuntimeException unwrap(Throwable cause, String operation) {
        if (cause instanceof BizException bizException) {
            return bizException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new BizException(
                ErrorCode.INTERNAL_ERROR,
                cause == null ? operation + " failed" : cause.getMessage());
    }

    private void recordTimerOnce(Timer.Sample sample,
                                 AtomicBoolean recorded,
                                 String operation,
                                 String result) {
        if (recorded.compareAndSet(false, true)) {
            metrics.stopExecutionTimer(sample, operation, result);
        }
    }

    private String resultLabel(Throwable failure) {
        if (failure == null) {
            return "success";
        }
        if (failure instanceof CancellationException) {
            return "cancelled";
        }
        if (failure instanceof BizException
                && failure.getMessage() != null
                && failure.getMessage().contains("timed out")) {
            return "timeout";
        }
        return "failure";
    }
}
