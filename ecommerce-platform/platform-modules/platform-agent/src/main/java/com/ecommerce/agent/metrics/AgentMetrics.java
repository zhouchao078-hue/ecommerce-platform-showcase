package com.ecommerce.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central metrics for Agent admission, execution, and LLM calls.
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeSseConnections = new AtomicInteger();

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("agent.sse.active.connections", activeSseConnections, AtomicInteger::get)
                .description("Active ticket-agent SSE connections")
                .register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopExecutionTimer(Timer.Sample sample, String operation, String result) {
        sample.stop(Timer.builder("agent.execution.duration")
                .description("Agent execution duration")
                .tag("operation", operation)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry));
    }

    public void recordAdmission(String operation, String result) {
        Counter.builder("agent.execution.admission")
                .description("Agent admission decisions")
                .tag("operation", operation)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void recordLlmCall(String provider, String result, long durationNanos) {
        Counter.builder("agent.llm.calls")
                .description("External LLM call outcomes")
                .tag("provider", provider)
                .tag("result", result)
                .register(registry)
                .increment();
        Timer.builder("agent.llm.duration")
                .description("External LLM call duration")
                .tag("provider", provider)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry)
                .record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    public void recordSseConnectionOpened() {
        activeSseConnections.incrementAndGet();
    }

    public void recordSseConnectionClosed() {
        activeSseConnections.updateAndGet(current -> Math.max(0, current - 1));
    }

    public void recordOutboxPublish(String transport, String result) {
        Counter.builder("agent.ticket.outbox.publish")
                .description("Ticket outbox publish outcomes")
                .tag("transport", transport)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void recordLeaseRecovery(String result, int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("agent.ticket.lease.recovery")
                .description("Recovered ticket agent run leases")
                .tag("result", result)
                .register(registry)
                .increment(count);
    }

    public void recordTicketRunQuotaRejected() {
        Counter.builder("agent.ticket.run.quota.rejected")
                .description("Rejected ticket agent runs due to distributed quota")
                .register(registry)
                .increment();
    }
}
