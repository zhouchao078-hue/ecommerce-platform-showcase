package com.ecommerce.support.service;

/**
 * Extension point for distributed resources tied to an AgentRun lifecycle.
 */
public interface AgentRunLifecycleListener {

    void onFinished(Long runId, Long userId, String status);
}
