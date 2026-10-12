package com.ecommerce.agent.ticket;

import com.ecommerce.auth.service.AuthService;
import com.ecommerce.agent.metrics.AgentMetrics;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.support.dto.CreateSupportTicketDTO;
import com.ecommerce.support.dto.CreateSupportMessageDTO;
import com.ecommerce.support.service.SupportTicketService;
import com.ecommerce.support.vo.AgentRunAcceptedVO;
import com.ecommerce.support.vo.AgentRunVO;
import com.ecommerce.support.vo.SupportTicketDetailVO;
import com.ecommerce.support.vo.SupportTicketVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketAgentClient {
    private final TicketAgentProperties properties;
    private final AuthService authService;
    private final SupportTicketService supportTicketService;
    private final TicketBusinessContextService businessContextService;
    private final TicketAgentRunExecutor runExecutor;
    private final TicketRunQuotaStore runQuotaStore;
    private final ObjectMapper objectMapper;
    private final AgentMetrics metrics;

    public AgentRunAcceptedVO submit(TicketSubmitRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Ticket agent is disabled");
        }
        Long userId = authService.getCurrentUserId();
        String reservationId = reserveQuota(userId);
        Long boundRunId = null;
        try {
            SupportTicketVO ticket = supportTicketService.create(userId, toCreateTicketDTO(request));
            Map<String, Object> payload = buildPayload(
                    userId,
                    ticket.getTicketNo(),
                    ticket.getSubject(),
                    request.getContent(),
                    request.getExternalMessageId());
            AgentRunAcceptedVO run = supportTicketService.startAgentRun(
                    ticket.getTicketNo(), writeJson(payload), request.getExternalMessageId());
            if (run.isReused()) {
                runQuotaStore.releaseReservation(userId, reservationId);
                return run;
            }
            if (!runQuotaStore.bindRun(
                    userId,
                    reservationId,
                    run.getRunId(),
                    properties.getActiveRunTtlSeconds())) {
                supportTicketService.finishAgentRun(
                        run.getRunId(),
                        "FAILED",
                        null,
                        "Failed to bind distributed run quota",
                        0L);
                runQuotaStore.releaseReservation(userId, reservationId);
                throw new BizException(
                        ErrorCode.TOO_MANY_REQUESTS,
                        "Support agent quota binding failed");
            }
            boundRunId = run.getRunId();
            payload.put("run_id", run.getRunNo());
            if (isLocalDispatch()) {
                runExecutor.submit(run.getRunId(), ticket.getTicketNo(), payload);
            }
            return run;
        } catch (RuntimeException ex) {
            if (boundRunId != null) {
                runQuotaStore.releaseRun(boundRunId);
            } else {
                runQuotaStore.releaseReservation(userId, reservationId);
            }
            throw ex;
        }
    }

    public AgentRunAcceptedVO followUp(String ticketNo, TicketFollowUpRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Ticket agent is disabled");
        }
        Long userId = authService.getCurrentUserId();
        String reservationId = reserveQuota(userId);
        Long boundRunId = null;
        try {
            SupportTicketDetailVO detail = supportTicketService.detail(ticketNo, userId);

            CreateSupportMessageDTO message = new CreateSupportMessageDTO();
            message.setContent(request.getContent());
            message.setMessageType("TEXT");
            message.setTraceId(request.getExternalMessageId());
            message.setClientMessageId(request.getExternalMessageId());
            supportTicketService.appendUserMessage(ticketNo, userId, message);

            Map<String, Object> payload = buildPayload(
                    userId,
                    ticketNo,
                    detail.getTicket().getSubject(),
                    request.getContent(),
                    request.getExternalMessageId());
            AgentRunAcceptedVO run = supportTicketService.startAgentRun(
                    ticketNo, writeJson(payload), request.getExternalMessageId());
            if (run.isReused()) {
                runQuotaStore.releaseReservation(userId, reservationId);
                return run;
            }
            if (!runQuotaStore.bindRun(
                    userId,
                    reservationId,
                    run.getRunId(),
                    properties.getActiveRunTtlSeconds())) {
                supportTicketService.finishAgentRun(
                        run.getRunId(),
                        "FAILED",
                        null,
                        "Failed to bind distributed run quota",
                        0L);
                runQuotaStore.releaseReservation(userId, reservationId);
                throw new BizException(
                        ErrorCode.TOO_MANY_REQUESTS,
                        "Support agent quota binding failed");
            }
            boundRunId = run.getRunId();
            payload.put("run_id", run.getRunNo());
            if (isLocalDispatch()) {
                runExecutor.submit(run.getRunId(), ticketNo, payload);
            }
            return run;
        } catch (RuntimeException ex) {
            if (boundRunId != null) {
                runQuotaStore.releaseRun(boundRunId);
            } else {
                runQuotaStore.releaseReservation(userId, reservationId);
            }
            throw ex;
        }
    }

    public AgentRunVO getRun(Long runId) {
        return supportTicketService.getAgentRun(runId, authService.getCurrentUserId());
    }

    public AgentRunVO cancelRun(Long runId) {
        AgentRunVO run = supportTicketService.requestCancelAgentRun(
                runId, authService.getCurrentUserId());
        runExecutor.cancel(runId);
        return supportTicketService.getAgentRun(runId, authService.getCurrentUserId());
    }

    private Map<String, Object> buildPayload(
            Long userId,
            String ticketNo,
            String subject,
            String content,
            String externalMessageId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenant_id", properties.getTenantId());
        payload.put("channel", "ecommerce-platform");
        payload.put("external_ticket_id", ticketNo);
        payload.put("external_message_id", nonBlankOrNew(externalMessageId));
        payload.put("customer_id", userId == null ? null : String.valueOf(userId));
        payload.put("subject", subject);
        payload.put("content", content);
        payload.put("business_context", businessContextService.build(userId, content));
        return payload;
    }

    private CreateSupportTicketDTO toCreateTicketDTO(TicketSubmitRequest request) {
        CreateSupportTicketDTO dto = new CreateSupportTicketDTO();
        String subject = request.getSubject() == null || request.getSubject().isBlank()
                ? request.getContent()
                : request.getSubject();
        dto.setSubject(subject.length() > 200 ? subject.substring(0, 200) : subject);
        dto.setContent(request.getContent());
        dto.setChannel("ecommerce-platform");
        dto.setClientRequestId(request.getExternalTicketId());
        return dto;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String nonBlankOrNew(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private boolean isLocalDispatch() {
        return "local".equalsIgnoreCase(properties.getDispatchMode());
    }

    private String reserveQuota(Long userId) {
        try {
            return runQuotaStore.reserve(
                    userId,
                    properties.getMaxActiveRunsPerUser(),
                    properties.getActiveRunTtlSeconds());
        } catch (BizException ex) {
            metrics.recordTicketRunQuotaRejected();
            throw ex;
        }
    }
}
