package com.ecommerce.agent.ticket;

import com.ecommerce.support.service.AgentRunLifecycleListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Releases the Redis quota once an AgentRun reaches a terminal state.
 */
@Component
@RequiredArgsConstructor
public class TicketRunQuotaLifecycleListener implements AgentRunLifecycleListener {

    private final TicketRunQuotaStore quotaStore;

    @Override
    public void onFinished(Long runId, Long userId, String status) {
        quotaStore.releaseRun(runId);
    }
}
