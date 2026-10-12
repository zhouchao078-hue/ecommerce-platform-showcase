package com.ecommerce.agent.llm;

import com.ecommerce.agent.config.AgentExecutionProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caps concurrent calls to external LLM providers.
 */
@Component
public class LlmConcurrencyLimiter {

    private final Semaphore permits;
    private final long acquireTimeoutMillis;

    public LlmConcurrencyLimiter(AgentExecutionProperties properties) {
        this.permits = new Semaphore(Math.max(1, properties.getLlmConcurrency()), true);
        this.acquireTimeoutMillis = Math.max(100L, properties.getLlmAcquireTimeoutMillis());
    }

    public Permit acquire() {
        try {
            if (!permits.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("LLM concurrency limit reached");
            }
            return new Permit(permits);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an LLM permit", ex);
        }
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore permits;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }
}
