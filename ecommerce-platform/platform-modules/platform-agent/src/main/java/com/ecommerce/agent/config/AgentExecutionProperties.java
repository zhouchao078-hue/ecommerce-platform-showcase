package com.ecommerce.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent execution limits and timeout configuration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent.execution")
public class AgentExecutionProperties {

    /** Maximum concurrent chat tasks across this JVM. */
    private int globalConcurrency = 32;

    /** Maximum concurrent chat tasks for one user across all JVMs. */
    private int perUserConcurrency = 2;

    /** Maximum queued chat tasks before requests are rejected. */
    private int chatQueueCapacity = 100;

    /** Maximum time a chat request may wait for an Agent result. */
    private int chatTimeoutSeconds = 120;

    /** Maximum concurrent calls to external LLM providers. */
    private int llmConcurrency = 8;

    /** Maximum time to wait for an LLM concurrency permit. */
    private long llmAcquireTimeoutMillis = 1000L;

    /** Maximum time one external LLM call may take. */
    private int llmTimeoutSeconds = 60;

    /** Failure percentage that opens the LLM circuit breaker. */
    private float llmCircuitFailureRateThreshold = 50.0F;

    /** Sliding window size used by the LLM circuit breaker. */
    private int llmCircuitSlidingWindowSize = 20;

    /** Minimum calls before the circuit breaker can open. */
    private int llmCircuitMinimumCalls = 10;

    /** Time the circuit remains open before half-open probes. */
    private long llmCircuitOpenSeconds = 30L;

    /** Number of permitted calls while half-open. */
    private int llmCircuitHalfOpenCalls = 3;

    /** Maximum recent messages retained in the Redis session history. */
    private int sessionHistoryLimit = 20;

    /** Redis session history time-to-live in seconds. */
    private long sessionTtlSeconds = 3600L;

    /** Number of threads used to poll ticket-agent SSE streams. */
    private int sseSchedulerPoolSize = 4;

    /** Ticket-agent SSE heartbeat interval in milliseconds. */
    private long sseHeartbeatMillis = 15000L;

    /** Ticket-agent SSE connection timeout in seconds. */
    private long sseTimeoutSeconds = 1800L;
}
