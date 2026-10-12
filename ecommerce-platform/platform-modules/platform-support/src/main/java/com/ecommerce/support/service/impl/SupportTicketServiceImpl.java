package com.ecommerce.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.support.dto.CreateSupportMessageDTO;
import com.ecommerce.support.dto.CreateSupportTicketDTO;
import com.ecommerce.support.enums.AgentRunStatus;
import com.ecommerce.support.enums.AgentStepStatus;
import com.ecommerce.support.enums.TicketMessageSenderType;
import com.ecommerce.support.enums.TicketPriority;
import com.ecommerce.support.enums.TicketStatus;
import com.ecommerce.support.mapper.SupportAgentRunMapper;
import com.ecommerce.support.mapper.SupportAgentRunEventMapper;
import com.ecommerce.support.mapper.SupportAgentRunStepMapper;
import com.ecommerce.support.mapper.SupportEventMapper;
import com.ecommerce.support.mapper.SupportMessageMapper;
import com.ecommerce.support.mapper.SupportTicketMapper;
import com.ecommerce.support.model.SupportAgentRun;
import com.ecommerce.support.model.SupportAgentRunEvent;
import com.ecommerce.support.model.SupportAgentRunStep;
import com.ecommerce.support.model.SupportEvent;
import com.ecommerce.support.model.SupportMessage;
import com.ecommerce.support.model.SupportTicket;
import com.ecommerce.support.service.SupportTicketService;
import com.ecommerce.support.service.SupportOutboxService;
import com.ecommerce.support.service.AgentRunLifecycleListener;
import com.ecommerce.support.vo.SupportMessageVO;
import com.ecommerce.support.vo.SupportTicketDetailVO;
import com.ecommerce.support.vo.SupportTicketVO;
import com.ecommerce.support.vo.AgentRunAcceptedVO;
import com.ecommerce.support.vo.AgentRunEventVO;
import com.ecommerce.support.vo.AgentRunVO;
import com.ecommerce.support.vo.AgentRunCheckpointVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketServiceImpl implements SupportTicketService {

    private static final String DEFAULT_CHANNEL = "web";
    private static final String DEFAULT_CATEGORY = "general";
    private static final String DEFAULT_VISIBILITY = "EXTERNAL";
    private static final String RESULT_PERSISTED_STEP = "RESULT_PERSISTED";
    private static final String LEASE_RECOVERED_STEP = "LEASE_RECOVERED";

    private final SupportTicketMapper ticketMapper;
    private final SupportMessageMapper messageMapper;
    private final SupportEventMapper eventMapper;
    private final SupportAgentRunMapper agentRunMapper;
    private final SupportAgentRunEventMapper agentRunEventMapper;
    private final SupportAgentRunStepMapper agentRunStepMapper;
    private final SupportOutboxService supportOutboxService;
    private final ObjectMapper objectMapper;
    private final List<AgentRunLifecycleListener> lifecycleListeners;

    @Value("${agent.ticket.dispatch-mode:local}")
    private String dispatchMode;

    @Value("${agent.ticket.kafka-enabled:false}")
    private boolean kafkaEnabled;

    @Value("${agent.ticket.kafka-run-topic:support.agent.run.events.v1}")
    private String kafkaRunTopic;

    @Value("${agent.ticket.model:deepseek-chat}")
    private String agentModel;

    @Value("${agent.ticket.prompt-version:support-agent-v1}")
    private String promptVersion;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupportTicketVO create(Long userId, CreateSupportTicketDTO dto) {
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        SupportTicket existing = findExisting(userId, dto.getClientRequestId());
        if (existing != null) {
            return toTicketVO(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setUserId(userId);
        ticket.setChannel(normalize(dto.getChannel(), DEFAULT_CHANNEL));
        ticket.setClientRequestId(blankToNull(dto.getClientRequestId()));
        ticket.setCategory(normalize(dto.getCategory(), DEFAULT_CATEGORY));
        ticket.setPriority(TicketPriority.NORMAL.name());
        ticket.setStatus(TicketStatus.NEW.getCode());
        ticket.setSubject(dto.getSubject().trim());
        ticket.setLastMessageAt(now);
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);
        try {
            ticketMapper.insert(ticket);
        } catch (DuplicateKeyException ex) {
            SupportTicket concurrent = findExisting(userId, dto.getClientRequestId());
            if (concurrent != null) {
                return toTicketVO(concurrent);
            }
            throw ex;
        }

        SupportMessage message = new SupportMessage();
        message.setTicketId(ticket.getId());
        message.setSenderType(TicketMessageSenderType.USER.name());
        message.setSenderId(userId);
        message.setMessageType("TEXT");
        message.setContent(dto.getContent().trim());
        message.setVisibility(DEFAULT_VISIBILITY);
        message.setCreateTime(now);
        messageMapper.insert(message);

        recordEvent(ticket.getId(), "TICKET_CREATED", null, ticket.getStatus(),
                TicketMessageSenderType.USER.name(), userId, null);

        log.info("Support ticket created: ticketNo={}, userId={}", ticket.getTicketNo(), userId);
        return toTicketVO(ticket);
    }

    @Override
    public IPage<SupportTicketVO> listMine(Long userId, Integer pageNum, Integer pageSize) {
        QueryWrapper<SupportTicket> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("last_message_at", "id");
        IPage<SupportTicket> page = ticketMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return page.convert(this::toTicketVO);
    }

    @Override
    public SupportTicketDetailVO detail(String ticketNo, Long userId) {
        SupportTicket ticket = requireOwnedTicket(ticketNo, userId);
        List<SupportMessageVO> messages = messageMapper.selectList(
                        new QueryWrapper<SupportMessage>()
                                .eq("ticket_id", ticket.getId())
                                .eq("visibility", DEFAULT_VISIBILITY)
                                .orderByAsc("id"))
                .stream()
                .map(this::toMessageVO)
                .toList();
        return SupportTicketDetailVO.builder()
                .ticket(toTicketVO(ticket))
                .messages(messages)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupportMessageVO appendUserMessage(String ticketNo, Long userId, CreateSupportMessageDTO dto) {
        SupportTicket ticket = requireOwnedTicket(ticketNo, userId);
        LocalDateTime now = LocalDateTime.now();
        String clientMessageId = blankToNull(dto.getClientMessageId());
        if (clientMessageId != null) {
            SupportMessage existing = messageMapper.selectOne(new QueryWrapper<SupportMessage>()
                    .eq("ticket_id", ticket.getId())
                    .eq("client_message_id", clientMessageId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return toMessageVO(existing, true);
            }
        }

        SupportMessage message = new SupportMessage();
        message.setTicketId(ticket.getId());
        message.setSenderType(TicketMessageSenderType.USER.name());
        message.setSenderId(userId);
        message.setMessageType(normalize(dto.getMessageType(), "TEXT"));
        message.setContent(dto.getContent().trim());
        message.setVisibility(DEFAULT_VISIBILITY);
        message.setTraceId(blankToNull(dto.getTraceId()));
        message.setClientMessageId(clientMessageId);
        message.setCreateTime(now);
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException ex) {
            if (clientMessageId != null) {
                SupportMessage concurrent = messageMapper.selectOne(
                        new QueryWrapper<SupportMessage>()
                                .eq("ticket_id", ticket.getId())
                                .eq("client_message_id", clientMessageId)
                                .last("LIMIT 1"));
                if (concurrent != null) {
                    return toMessageVO(concurrent, true);
                }
            }
            throw ex;
        }

        ticket.setLastMessageAt(now);
        ticket.setUpdateTime(now);
        if (ticket.getStatus().equals(TicketStatus.RESOLVED.getCode())
                || ticket.getStatus().equals(TicketStatus.CLOSED.getCode())) {
            Integer fromStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.NEW.getCode());
            ticket.setResolvedAt(null);
            ticket.setClosedAt(null);
            recordEvent(ticket.getId(), "TICKET_REOPENED", fromStatus, ticket.getStatus(),
                    TicketMessageSenderType.USER.name(), userId, null);
        } else {
            recordEvent(ticket.getId(), "MESSAGE_ADDED", ticket.getStatus(), ticket.getStatus(),
                    TicketMessageSenderType.USER.name(), userId, null);
        }
        ticketMapper.updateById(ticket);
        return toMessageVO(message, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentRunAcceptedVO startAgentRun(String ticketNo, String requestPayload,
                                            String clientRequestId) {
        SupportTicket ticket = requireTicket(ticketNo);
        String normalizedClientRequestId = blankToNull(clientRequestId);
        if (normalizedClientRequestId != null) {
            SupportAgentRun existing = agentRunMapper.selectOne(new QueryWrapper<SupportAgentRun>()
                    .eq("ticket_id", ticket.getId())
                    .eq("client_request_id", normalizedClientRequestId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return toAcceptedVO(existing, ticket.getTicketNo(), true);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        SupportAgentRun run = new SupportAgentRun();
        run.setRunNo(generateRunNo());
        run.setTicketId(ticket.getId());
        run.setStatus(AgentRunStatus.PENDING.name());
        run.setProvider("customer-ticket-copilot");
        run.setModel(agentModel);
        run.setPromptVersion(promptVersion);
        run.setRequestPayload(requestPayload);
        run.setClientRequestId(normalizedClientRequestId);
        run.setCancelRequested(false);
        run.setVersion(0);
        run.setStartedAt(now);
        run.setCreateTime(now);
        run.setUpdateTime(now);
        try {
            agentRunMapper.insert(run);
        } catch (DuplicateKeyException ex) {
            if (normalizedClientRequestId != null) {
                SupportAgentRun concurrent = agentRunMapper.selectOne(
                        new QueryWrapper<SupportAgentRun>()
                                .eq("ticket_id", ticket.getId())
                                .eq("client_request_id", normalizedClientRequestId)
                                .last("LIMIT 1"));
                if (concurrent != null) {
                    return toAcceptedVO(concurrent, ticket.getTicketNo(), true);
                }
            }
            throw ex;
        }
        if ("rabbit".equalsIgnoreCase(dispatchMode)) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("runId", run.getId());
            message.put("runNo", run.getRunNo());
            message.put("ticketNo", ticket.getTicketNo());
            message.put("requestPayload", requestPayload);
            message.put("priority", ticket.getPriority());
            supportOutboxService.enqueue(
                    "AGENT_RUN",
                    run.getId(),
                    "AGENT_RUN_REQUESTED",
                    "RABBIT",
                    "support.agent.exchange",
                    writeJson(message));
        }
        publishRunEvent("AGENT_RUN_REQUESTED", run.getId(), null);
        return toAcceptedVO(run, ticket.getTicketNo(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAgentRunRunning(Long runId) {
        int updated = agentRunMapper.update(null, new UpdateWrapper<SupportAgentRun>()
                .eq("id", runId)
                .eq("status", AgentRunStatus.PENDING.name())
                .set("status", AgentRunStatus.RUNNING.name())
                .set("updated_at", LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated > 0 && kafkaEnabled) {
            publishRunEvent("AGENT_RUN_STARTED", runId, null);
        }
    }

    @Override
    public AgentRunVO getAgentRun(Long runId, Long userId) {
        SupportAgentRun run = requireOwnedRun(runId, userId);
        SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
        return toAgentRunVO(run, ticket.getTicketNo());
    }

    @Override
    public List<AgentRunEventVO> listAgentRunEvents(Long runId, Long userId, Long afterEventId) {
        SupportAgentRun run = requireOwnedRun(runId, userId);
        long cursor = afterEventId == null ? 0L : Math.max(0L, afterEventId);
        return agentRunEventMapper.selectList(new QueryWrapper<SupportAgentRunEvent>()
                        .eq("run_id", runId)
                        .gt("id", cursor)
                        .orderByAsc("id")
                        .last("LIMIT 200"))
                .stream()
                .map(event -> toAgentRunEventVO(event, run))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentRunVO requestCancelAgentRun(Long runId, Long userId) {
        SupportAgentRun run = requireOwnedRun(runId, userId);
        if (isTerminal(run.getStatus())) {
            SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
            return toAgentRunVO(run, ticket.getTicketNo());
        }
        LocalDateTime now = LocalDateTime.now();
        int updatedCount = agentRunMapper.update(null, new UpdateWrapper<SupportAgentRun>()
                .eq("id", runId)
                .in("status", AgentRunStatus.PENDING.name(), AgentRunStatus.RUNNING.name())
                .set("status", AgentRunStatus.CANCEL_REQUESTED.name())
                .set("cancel_requested", true)
                .set("updated_at", now)
                .setSql("version = version + 1"));
        SupportAgentRun updated = agentRunMapper.selectById(runId);
        SupportTicket ticket = ticketMapper.selectById(updated.getTicketId());
        if (updatedCount > 0 && "rabbit".equalsIgnoreCase(dispatchMode)) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("runId", updated.getId());
            message.put("runNo", updated.getRunNo());
            message.put("ticketNo", ticket.getTicketNo());
            message.put("requestedAt", now);
            supportOutboxService.enqueue(
                    "AGENT_RUN",
                    updated.getId(),
                    "AGENT_RUN_CANCEL_REQUESTED",
                    "RABBIT",
                    "support.agent.exchange",
                    writeJson(message));
        }
        if (updatedCount > 0 && kafkaEnabled) {
            publishRunEvent("AGENT_RUN_CANCEL_REQUESTED", runId, null);
        }
        return toAgentRunVO(updated, ticket.getTicketNo());
    }

    @Override
    public boolean isAgentRunCancelled(Long runId) {
        SupportAgentRun run = agentRunMapper.selectById(runId);
        return run == null
                || Boolean.TRUE.equals(run.getCancelRequested())
                || AgentRunStatus.CANCEL_REQUESTED.name().equals(run.getStatus())
                || AgentRunStatus.CANCELLED.name().equals(run.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentRunVO startAgentRunLease(Long runId, String workerId, int leaseSeconds) {
        SupportAgentRun run = agentRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent run not found");
        }
        if (Boolean.TRUE.equals(run.getCancelRequested())
                || AgentRunStatus.CANCEL_REQUESTED.name().equals(run.getStatus())
                || AgentRunStatus.CANCELLED.name().equals(run.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "Agent run was cancelled");
        }
        if (isTerminal(run.getStatus())) {
            SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
            return toAgentRunVO(run, ticket.getTicketNo());
        }

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<SupportAgentRun> update = new UpdateWrapper<SupportAgentRun>()
                .eq("id", runId)
                .set("status", AgentRunStatus.RUNNING.name())
                .set("worker_id", workerId)
                .set("lease_expires_at", now.plusSeconds(leaseSeconds))
                .set("last_heartbeat_at", now)
                .set("updated_at", now)
                .setSql("version = version + 1");
        if (AgentRunStatus.PENDING.name().equals(run.getStatus())) {
            update.eq("status", AgentRunStatus.PENDING.name());
        } else {
            update.eq("status", AgentRunStatus.RUNNING.name())
                    .and(wrapper -> wrapper
                            .isNull("lease_expires_at")
                            .or()
                            .lt("lease_expires_at", now));
        }
        int updated = agentRunMapper.update(null, update);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "Agent run is leased by another worker");
        }
        recordAgentStep(
                runId,
                "WORKER_CLAIMED",
                AgentStepStatus.SUCCEEDED.name(),
                workerId,
                null,
                null,
                1,
                0L);
        SupportAgentRun updatedRun = agentRunMapper.selectById(runId);
        SupportTicket ticket = ticketMapper.selectById(updatedRun.getTicketId());
        return toAgentRunVO(updatedRun, ticket.getTicketNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentRunVO heartbeatAgentRun(Long runId, String workerId, int leaseSeconds) {
        SupportAgentRun run = agentRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent run not found");
        }
        if (isTerminal(run.getStatus())) {
            SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
            return toAgentRunVO(run, ticket.getTicketNo());
        }
        if (!workerId.equals(run.getWorkerId())) {
            throw new BizException(ErrorCode.CONFLICT, "Agent run lease owner mismatch");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = agentRunMapper.update(null, new UpdateWrapper<SupportAgentRun>()
                .eq("id", runId)
                .eq("worker_id", workerId)
                .set("lease_expires_at", now.plusSeconds(leaseSeconds))
                .set("last_heartbeat_at", now)
                .set("updated_at", now));
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "Agent run lease was lost");
        }
        SupportAgentRun updatedRun = agentRunMapper.selectById(runId);
        SupportTicket ticket = ticketMapper.selectById(updatedRun.getTicketId());
        return toAgentRunVO(updatedRun, ticket.getTicketNo());
    }

    @Override
    public AgentRunCheckpointVO getAgentRunCheckpoint(Long runId) {
        SupportAgentRun run = agentRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent run not found");
        }
        SupportAgentRunStep modelStep = agentRunStepMapper.selectOne(
                new QueryWrapper<SupportAgentRunStep>()
                        .eq("run_id", runId)
                        .eq("step_name", "MODEL_COMPLETED")
                        .eq("status", AgentStepStatus.SUCCEEDED.name())
                        .orderByDesc("id")
                        .last("LIMIT 1"));
        return AgentRunCheckpointVO.builder()
                .status(run.getStatus())
                .cancelRequested(run.getCancelRequested())
                .lastCompletedStep(run.getLastCompletedStep())
                .modelResponsePayload(modelStep == null ? null : modelStep.getOutputPayload())
                .build();
    }

    @Override
    public long countActiveAgentRuns(Long userId) {
        if (userId == null) {
            return 0L;
        }
        List<Long> ticketIds = ticketMapper.selectList(new QueryWrapper<SupportTicket>()
                        .eq("user_id", userId))
                .stream()
                .map(SupportTicket::getId)
                .toList();
        if (ticketIds.isEmpty()) {
            return 0L;
        }
        return agentRunMapper.selectCount(new QueryWrapper<SupportAgentRun>()
                .in("ticket_id", ticketIds)
                .in("status",
                        AgentRunStatus.PENDING.name(),
                        AgentRunStatus.RUNNING.name(),
                        AgentRunStatus.CANCEL_REQUESTED.name()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverExpiredAgentRuns(int batchSize) {
        if (!"rabbit".equalsIgnoreCase(dispatchMode)) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        List<SupportAgentRun> expiredRuns = agentRunMapper.selectList(new QueryWrapper<SupportAgentRun>()
                .eq("status", AgentRunStatus.RUNNING.name())
                .eq("cancel_requested", false)
                .lt("lease_expires_at", now)
                .orderByAsc("lease_expires_at", "id")
                .last("LIMIT " + Math.max(1, Math.min(batchSize, 100))));
        int recovered = 0;
        for (SupportAgentRun run : expiredRuns) {
            SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
            if (ticket == null) {
                log.warn("Skip expired agent run without ticket: runId={}", run.getId());
                continue;
            }
            if (run.getRequestPayload() == null || run.getRequestPayload().isBlank()) {
                finishAgentRun(
                        run.getId(),
                        AgentRunStatus.FAILED.name(),
                        null,
                        "Recovered run is missing request payload",
                        0L);
                recordAgentStep(
                        run.getId(),
                        "RUN_FAILED",
                        AgentStepStatus.FAILED.name(),
                        null,
                        null,
                        "Recovered run is missing request payload",
                        1,
                        0L);
                continue;
            }
            int updated = agentRunMapper.update(null, new UpdateWrapper<SupportAgentRun>()
                    .eq("id", run.getId())
                    .eq("status", AgentRunStatus.RUNNING.name())
                    .eq("cancel_requested", false)
                    .lt("lease_expires_at", now)
                    .set("status", AgentRunStatus.PENDING.name())
                    .set("worker_id", null)
                    .set("lease_expires_at", null)
                    .set("last_heartbeat_at", null)
                    .set("updated_at", now)
                    .setSql("version = version + 1"));
            if (updated == 0) {
                continue;
            }
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("runId", run.getId());
            message.put("runNo", run.getRunNo());
            message.put("ticketNo", ticket.getTicketNo());
            message.put("requestPayload", run.getRequestPayload());
            message.put("priority", ticket.getPriority());
            message.put("recovered", true);
            supportOutboxService.enqueue(
                    "AGENT_RUN",
                    run.getId(),
                    "AGENT_RUN_REQUESTED",
                    "RABBIT",
                    "support.agent.exchange",
                    writeJson(message));
            recordAgentStep(
                    run.getId(),
                    LEASE_RECOVERED_STEP,
                    AgentStepStatus.SUCCEEDED.name(),
                    run.getWorkerId(),
                    null,
                    null,
                    1,
                    0L);
            recovered++;
        }
        return recovered;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordAgentStep(Long runId, String stepName, String status, String inputHash,
                                String outputPayload, String errorMessage, Integer attempt,
                                Long durationMs) {
        LocalDateTime now = LocalDateTime.now();
        SupportAgentRunStep step = new SupportAgentRunStep();
        step.setRunId(runId);
        step.setStepName(stepName);
        step.setStatus(status);
        step.setInputHash(blankToNull(inputHash));
        step.setOutputPayload(outputPayload);
        step.setErrorMessage(errorMessage);
        step.setAttempt(attempt == null ? 1 : attempt);
        step.setStartedAt(now);
        step.setFinishedAt(now);
        step.setDurationMs(durationMs);
        step.setCreateTime(now);
        step.setUpdateTime(now);
        agentRunStepMapper.insert(step);

        if (AgentStepStatus.SUCCEEDED.name().equals(status)) {
            agentRunMapper.update(null, new UpdateWrapper<SupportAgentRun>()
                    .eq("id", runId)
                    .set("last_completed_step", stepName)
                    .set("updated_at", now));
        }
        publishStepEvent(runId, stepName, status, errorMessage, attempt, durationMs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishAgentRun(Long runId, String status, String responsePayload,
                               String errorMessage, Long durationMs) {
        SupportAgentRun run = agentRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent run not found");
        }
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<SupportAgentRun> update = new UpdateWrapper<SupportAgentRun>()
                .eq("id", runId)
                .set("status", status)
                .set("response_payload", responsePayload)
                .set("error_message", errorMessage)
                .set("finished_at", now)
                .set("duration_ms", durationMs)
                .set("updated_at", now)
                .setSql("version = version + 1");
        if (AgentRunStatus.SUCCEEDED.name().equals(status)) {
            update.eq("status", AgentRunStatus.RUNNING.name())
                    .eq("cancel_requested", false);
        } else if (AgentRunStatus.CANCELLED.name().equals(status)) {
            update.eq("status", AgentRunStatus.CANCEL_REQUESTED.name());
        } else {
            update.in("status",
                    AgentRunStatus.PENDING.name(),
                    AgentRunStatus.RUNNING.name(),
                    AgentRunStatus.CANCEL_REQUESTED.name());
        }
        int updated = agentRunMapper.update(null, update);
        if (updated > 0 && isTerminal(status)) {
            SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
            Long userId = ticket == null ? null : ticket.getUserId();
            notifyRunFinishedAfterCommit(runId, userId, status);
        }
        if (updated > 0 && kafkaEnabled) {
            String eventType = switch (status) {
                case "SUCCEEDED" -> "AGENT_RUN_SUCCEEDED";
                case "FAILED" -> "AGENT_RUN_FAILED";
                case "CANCELLED" -> "AGENT_RUN_CANCELLED";
                case "TIMED_OUT" -> "AGENT_RUN_TIMED_OUT";
                default -> null;
            };
            if (eventType != null) {
                publishRunEvent(eventType, runId, errorMessage);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean claimAgentResult(Long runId) {
        int updated = agentRunMapper.update(null, new UpdateWrapper<SupportAgentRun>()
                .eq("id", runId)
                .eq("status", AgentRunStatus.SUCCEEDED.name())
                .eq("cancel_requested", false)
                .and(wrapper -> wrapper
                        .isNull("last_completed_step")
                        .or()
                        .ne("last_completed_step", RESULT_PERSISTED_STEP))
                .set("last_completed_step", RESULT_PERSISTED_STEP)
                .set("updated_at", LocalDateTime.now()));
        return updated > 0;
    }

    private void notifyRunFinishedAfterCommit(Long runId, Long userId, String status) {
        Runnable notification = () -> {
            for (AgentRunLifecycleListener listener : lifecycleListeners) {
                try {
                    listener.onFinished(runId, userId, status);
                } catch (Exception ex) {
                    log.warn("AgentRun lifecycle listener failed: runId={}, listener={}, reason={}",
                            runId, listener.getClass().getSimpleName(), ex.getMessage());
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            notification.run();
                        }
                    });
        } else {
            notification.run();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyAgentResult(Long runId, String ticketNo, String content, String traceId,
                                 String structuredPayload, boolean humanRequired) {
        if (!claimAgentResult(runId)) {
            log.info("Agent result already claimed, skip duplicate callback: runId={}", runId);
            return;
        }
        applyClaimedAgentResult(
                runId,
                ticketNo,
                content,
                traceId,
                structuredPayload,
                humanRequired);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyClaimedAgentResult(Long runId, String ticketNo, String content,
                                        String traceId, String structuredPayload,
                                        boolean humanRequired) {
        SupportAgentRun run = agentRunMapper.selectById(runId);
        if (run == null
                || !AgentRunStatus.SUCCEEDED.name().equals(run.getStatus())
                || Boolean.TRUE.equals(run.getCancelRequested())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        SupportTicket ticket = requireTicket(ticketNo);

        SupportMessage message = new SupportMessage();
        message.setTicketId(ticket.getId());
        message.setSenderType(TicketMessageSenderType.AGENT.name());
        message.setMessageType("TEXT");
        message.setContent(content);
        message.setStructuredPayload(structuredPayload);
        message.setVisibility(DEFAULT_VISIBILITY);
        message.setTraceId(blankToNull(traceId));
        message.setCreateTime(now);
        messageMapper.insert(message);

        Integer fromStatus = ticket.getStatus();
        ticket.setStatus(humanRequired
                ? TicketStatus.WAITING_HUMAN.getCode()
                : TicketStatus.TRIAGED.getCode());
        ticket.setLastMessageAt(now);
        ticket.setUpdateTime(now);
        ticketMapper.updateById(ticket);
        recordEvent(ticket.getId(),
                humanRequired ? "HUMAN_REVIEW_REQUIRED" : "AGENT_ANALYZED",
                fromStatus,
                ticket.getStatus(),
                TicketMessageSenderType.AGENT.name(),
                null,
                structuredPayload);
        recordAgentStep(
                runId,
                RESULT_PERSISTED_STEP,
                AgentStepStatus.SUCCEEDED.name(),
                null,
                structuredPayload,
                null,
                1,
                0L);
    }

    private SupportTicket findExisting(Long userId, String clientRequestId) {
        String normalized = blankToNull(clientRequestId);
        if (normalized == null) {
            return null;
        }
        return ticketMapper.selectOne(new QueryWrapper<SupportTicket>()
                .eq("user_id", userId)
                .eq("client_request_id", normalized)
                .last("LIMIT 1"));
    }

    private void publishRunEvent(String eventType, Long runId, String errorMessage) {
        SupportAgentRun run = agentRunMapper.selectById(runId);
        if (run == null) {
            return;
        }
        SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
        if (ticket == null) {
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("errorMessage", errorMessage);
        recordAgentRunEvent(eventType, run, ticket, extra);
        if (kafkaEnabled) {
            enqueueKafkaEvent(eventType, run, ticket, extra);
        }
    }

    private void publishStepEvent(Long runId, String stepName, String status,
                                  String errorMessage, Integer attempt, Long durationMs) {
        if (!kafkaEnabled) {
            return;
        }
        String eventType = switch (stepName) {
            case "WORKER_CLAIMED" -> "AGENT_RUN_STARTED";
            case "MODEL_COMPLETED" -> "MODEL_COMPLETED";
            case "GUARDRAIL_VALIDATED" -> "GUARDRAIL_VALIDATED";
            case "TOOL_CALLED" -> "TOOL_CALLED";
            case "TOOL_FAILED" -> "TOOL_FAILED";
            case LEASE_RECOVERED_STEP -> "AGENT_RUN_RECOVERED";
            default -> null;
        };
        if (eventType == null) {
            return;
        }
        SupportAgentRun run = agentRunMapper.selectById(runId);
        if (run == null) {
            return;
        }
        SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
        if (ticket == null) {
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("stepName", stepName);
        extra.put("stepStatus", status);
        extra.put("attempt", attempt);
        extra.put("durationMs", durationMs);
        extra.put("errorMessage", errorMessage);
        recordAgentRunEvent(eventType, run, ticket, extra);
        if (kafkaEnabled) {
            enqueueKafkaEvent(eventType, run, ticket, extra);
        }
    }

    private void recordAgentRunEvent(String eventType, SupportAgentRun run, SupportTicket ticket,
                                     Map<String, Object> extra) {
        LocalDateTime now = LocalDateTime.now();
        SupportAgentRunEvent event = new SupportAgentRunEvent();
        event.setRunId(run.getId());
        event.setEventType(eventType);
        event.setStatus(run.getStatus());
        event.setLastCompletedStep(run.getLastCompletedStep());
        event.setWorkerId(run.getWorkerId());
        event.setLeaseExpiresAt(run.getLeaseExpiresAt());
        event.setLastHeartbeatAt(run.getLastHeartbeatAt());
        Object eventError = extra == null ? null : extra.get("errorMessage");
        event.setErrorMessage(eventError == null
                ? run.getErrorMessage()
                : String.valueOf(eventError));
        event.setPayload(writeJson(extra == null ? Map.of() : extra));
        event.setCreateTime(now);
        agentRunEventMapper.insert(event);
    }

    private void enqueueKafkaEvent(String eventType, SupportAgentRun run, SupportTicket ticket,
                                   Map<String, Object> extra) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("occurredAt", LocalDateTime.now());
        event.put("runId", run.getId());
        event.put("runNo", run.getRunNo());
        event.put("ticketId", ticket.getId());
        event.put("ticketNo", ticket.getTicketNo());
        event.put("status", run.getStatus());
        event.put("priority", ticket.getPriority());
        if (extra != null) {
            extra.forEach(event::putIfAbsent);
        }
        supportOutboxService.enqueue(
                "AGENT_RUN",
                run.getId(),
                eventType,
                "KAFKA",
                kafkaRunTopic,
                writeJson(event));
    }

    private SupportTicket requireOwnedTicket(String ticketNo, Long userId) {
        SupportTicket ticket = ticketMapper.selectOne(new QueryWrapper<SupportTicket>()
                .eq("ticket_no", ticketNo)
                .last("LIMIT 1"));
        if (ticket == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Support ticket not found");
        }
        if (!ticket.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "No permission to access this ticket");
        }
        return ticket;
    }

    private SupportTicket requireTicket(String ticketNo) {
        SupportTicket ticket = ticketMapper.selectOne(new QueryWrapper<SupportTicket>()
                .eq("ticket_no", ticketNo)
                .last("LIMIT 1"));
        if (ticket == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Support ticket not found");
        }
        return ticket;
    }

    private SupportAgentRun requireOwnedRun(Long runId, Long userId) {
        SupportAgentRun run = agentRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent run not found");
        }
        SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
        if (ticket == null || !ticket.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "No permission to access this agent run");
        }
        return run;
    }

    private void recordEvent(Long ticketId, String eventType, Integer fromStatus, Integer toStatus,
                             String actorType, Long actorId, String payload) {
        SupportEvent event = new SupportEvent();
        event.setTicketId(ticketId);
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setPayload(payload);
        event.setCreateTime(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private SupportTicketVO toTicketVO(SupportTicket ticket) {
        return SupportTicketVO.builder()
                .ticketNo(ticket.getTicketNo())
                .subject(ticket.getSubject())
                .summary(ticket.getSummary())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .statusDesc(TicketStatus.fromCode(ticket.getStatus()).getDescription())
                .lastMessageAt(ticket.getLastMessageAt())
                .createTime(ticket.getCreateTime())
                .build();
    }

    private SupportMessageVO toMessageVO(SupportMessage message) {
        return toMessageVO(message, false);
    }

    private SupportMessageVO toMessageVO(SupportMessage message, boolean duplicate) {
        return SupportMessageVO.builder()
                .id(message.getId())
                .senderType(message.getSenderType())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .visibility(message.getVisibility())
                .clientMessageId(message.getClientMessageId())
                .duplicate(duplicate)
                .createTime(message.getCreateTime())
                .build();
    }

    private AgentRunAcceptedVO toAcceptedVO(SupportAgentRun run, String ticketNo, boolean reused) {
        return AgentRunAcceptedVO.builder()
                .runId(run.getId())
                .runNo(run.getRunNo())
                .ticketNo(ticketNo)
                .status(run.getStatus())
                .reused(reused)
                .build();
    }

    private AgentRunVO toAgentRunVO(SupportAgentRun run, String ticketNo) {
        return AgentRunVO.builder()
                .runId(run.getId())
                .runNo(run.getRunNo())
                .ticketNo(ticketNo)
                .status(run.getStatus())
                .cancelRequested(run.getCancelRequested())
                .lastCompletedStep(run.getLastCompletedStep())
                .workerId(run.getWorkerId())
                .leaseExpiresAt(run.getLeaseExpiresAt())
                .lastHeartbeatAt(run.getLastHeartbeatAt())
                .errorMessage(run.getErrorMessage())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getDurationMs())
                .build();
    }

    private AgentRunEventVO toAgentRunEventVO(SupportAgentRunEvent event, SupportAgentRun run) {
        return AgentRunEventVO.builder()
                .eventId(event.getId())
                .eventType(event.getEventType())
                .runId(event.getRunId())
                .runNo(run.getRunNo())
                .status(event.getStatus())
                .lastCompletedStep(event.getLastCompletedStep())
                .workerId(event.getWorkerId())
                .leaseExpiresAt(event.getLeaseExpiresAt())
                .lastHeartbeatAt(event.getLastHeartbeatAt())
                .errorMessage(event.getErrorMessage())
                .occurredAt(event.getCreateTime())
                .build();
    }

    private boolean isTerminal(String status) {
        return AgentRunStatus.SUCCEEDED.name().equals(status)
                || AgentRunStatus.FAILED.name().equals(status)
                || AgentRunStatus.TIMED_OUT.name().equals(status)
                || AgentRunStatus.CANCELLED.name().equals(status);
    }

    private String generateTicketNo() {
        return "SP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String generateRunNo() {
        return "AR" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String normalize(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
