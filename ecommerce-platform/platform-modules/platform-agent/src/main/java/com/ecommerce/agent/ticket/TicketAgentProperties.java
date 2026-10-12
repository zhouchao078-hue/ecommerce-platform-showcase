package com.ecommerce.agent.ticket;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent.ticket")
public class TicketAgentProperties {
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8000";
    private String tenantId = "ecommerce";
    private int timeoutSeconds = 45;
    private int maxConcurrentRuns = 8;
    private int maxQueuedRuns = 100;
    private int maxActiveRunsPerUser = 3;
    private long activeRunTtlSeconds = 86400L;
    private String dispatchMode = "local";
    private String internalToken = "change-me";
    private String rabbitExchange = "support.agent.exchange";
    private String rabbitRunRoutingKey = "agent.run.normal";
    private String rabbitCancelRoutingKey = "agent.cancel";
    private long outboxLeaseSeconds = 60L;
    private long leaseRecoveryIntervalMs = 15000L;
    private int leaseRecoveryBatchSize = 20;
    private boolean kafkaEnabled = false;
    private String kafkaRunTopic = "support.agent.run.events.v1";
}
