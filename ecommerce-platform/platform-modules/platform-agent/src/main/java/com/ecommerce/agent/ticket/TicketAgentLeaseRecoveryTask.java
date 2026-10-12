package com.ecommerce.agent.ticket;

import com.ecommerce.agent.metrics.AgentMetrics;
import com.ecommerce.support.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.ticket", name = "dispatch-mode", havingValue = "rabbit")
public class TicketAgentLeaseRecoveryTask {

    private final SupportTicketService supportTicketService;
    private final TicketAgentProperties properties;
    private final AgentMetrics metrics;

    @Scheduled(fixedDelayString = "${agent.ticket.lease-recovery-interval-ms:15000}")
    public void recoverExpiredRuns() {
        try {
            int recovered = supportTicketService.recoverExpiredAgentRuns(
                    properties.getLeaseRecoveryBatchSize());
            metrics.recordLeaseRecovery("success", recovered);
            if (recovered > 0) {
                log.warn("Recovered expired ticket agent runs: count={}", recovered);
            }
        } catch (Exception ex) {
            metrics.recordLeaseRecovery("failure", 1);
            log.error("Failed to recover expired ticket agent runs", ex);
        }
    }
}
