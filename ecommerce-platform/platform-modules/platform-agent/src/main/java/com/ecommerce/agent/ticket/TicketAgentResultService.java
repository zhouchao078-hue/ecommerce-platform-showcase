package com.ecommerce.agent.ticket;

import com.ecommerce.support.enums.AgentRunStatus;
import com.ecommerce.support.enums.AgentStepStatus;
import com.ecommerce.support.service.SupportTicketService;
import com.ecommerce.support.vo.AgentRunCheckpointVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketAgentResultService {

    private static final String RESULT_PERSISTED_STEP = "RESULT_PERSISTED";

    private final SupportTicketService supportTicketService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long runId, String ticketNo, TicketAgentResponse response,
                         Long durationMs) {
        AgentRunCheckpointVO checkpoint = supportTicketService.getAgentRunCheckpoint(runId);
        if (RESULT_PERSISTED_STEP.equals(checkpoint.getLastCompletedStep())
                || shouldIgnoreCallback(checkpoint)) {
            return;
        }
        supportTicketService.finishAgentRun(
                runId,
                AgentRunStatus.SUCCEEDED.name(),
                writeJson(response),
                null,
                durationMs);
        if (!supportTicketService.claimAgentResult(runId)) {
            return;
        }
        supportTicketService.recordAgentStep(
                runId,
                "MODEL_COMPLETED",
                AgentStepStatus.SUCCEEDED.name(),
                null,
                writeJson(response),
                null,
                1,
                durationMs);
        supportTicketService.recordAgentStep(
                runId,
                "GUARDRAIL_VALIDATED",
                AgentStepStatus.SUCCEEDED.name(),
                null,
                writeJson(response.getValidationResult()),
                null,
                1,
                0L);
        supportTicketService.applyClaimedAgentResult(
                runId,
                ticketNo,
                responseText(response),
                response.getTraceId(),
                writeJson(response),
                Boolean.TRUE.equals(response.getHumanRequired()));
    }

    public void fail(Long runId, String errorMessage, Long durationMs) {
        AgentRunCheckpointVO checkpoint = supportTicketService.getAgentRunCheckpoint(runId);
        if (shouldIgnoreCallback(checkpoint)) {
            return;
        }
        supportTicketService.recordAgentStep(
                runId,
                "RUN_FAILED",
                AgentStepStatus.FAILED.name(),
                null,
                null,
                errorMessage,
                1,
                durationMs);
        supportTicketService.finishAgentRun(
                runId,
                AgentRunStatus.FAILED.name(),
                null,
                errorMessage,
                durationMs);
    }

    private boolean shouldIgnoreCallback(AgentRunCheckpointVO checkpoint) {
        if (Boolean.TRUE.equals(checkpoint.getCancelRequested())) {
            return true;
        }
        String status = checkpoint.getStatus();
        return AgentRunStatus.CANCELLED.name().equals(status)
                || AgentRunStatus.CANCEL_REQUESTED.name().equals(status)
                || AgentRunStatus.FAILED.name().equals(status)
                || AgentRunStatus.TIMED_OUT.name().equals(status);
    }

    private String responseText(TicketAgentResponse response) {
        if (response.getFinalResponse() != null && !response.getFinalResponse().isBlank()) {
            return response.getFinalResponse();
        }
        if (response.getDraftResponse() != null && !response.getDraftResponse().isBlank()) {
            return response.getDraftResponse();
        }
        return "The support request has been received and is awaiting review.";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
