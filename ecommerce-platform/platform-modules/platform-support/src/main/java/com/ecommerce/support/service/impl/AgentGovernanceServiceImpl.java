package com.ecommerce.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.support.dto.AgentRunEvaluationDTO;
import com.ecommerce.support.dto.CreateSupportMessageDTO;
import com.ecommerce.support.enums.AgentRunStatus;
import com.ecommerce.support.enums.TicketMessageSenderType;
import com.ecommerce.support.enums.TicketStatus;
import com.ecommerce.support.mapper.SupportAgentEvaluationMapper;
import com.ecommerce.support.mapper.SupportAgentRunEventMapper;
import com.ecommerce.support.mapper.SupportAgentRunMapper;
import com.ecommerce.support.mapper.SupportAgentRunStepMapper;
import com.ecommerce.support.mapper.SupportEventMapper;
import com.ecommerce.support.mapper.SupportMessageMapper;
import com.ecommerce.support.mapper.SupportTicketMapper;
import com.ecommerce.support.model.SupportAgentEvaluation;
import com.ecommerce.support.model.SupportAgentRun;
import com.ecommerce.support.model.SupportAgentRunEvent;
import com.ecommerce.support.model.SupportAgentRunStep;
import com.ecommerce.support.model.SupportEvent;
import com.ecommerce.support.model.SupportMessage;
import com.ecommerce.support.model.SupportTicket;
import com.ecommerce.support.service.AgentGovernanceService;
import com.ecommerce.support.vo.AgentEvaluationVO;
import com.ecommerce.support.vo.AgentQualitySummaryVO;
import com.ecommerce.support.vo.AgentRunAdminDetailVO;
import com.ecommerce.support.vo.AgentRunAdminVO;
import com.ecommerce.support.vo.AgentRunEventVO;
import com.ecommerce.support.vo.AgentRunStepVO;
import com.ecommerce.support.vo.SupportMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentGovernanceServiceImpl implements AgentGovernanceService {

    private static final String DEFAULT_VISIBILITY = "EXTERNAL";

    private final SupportAgentRunMapper agentRunMapper;
    private final SupportAgentRunStepMapper agentRunStepMapper;
    private final SupportAgentRunEventMapper agentRunEventMapper;
    private final SupportAgentEvaluationMapper evaluationMapper;
    private final SupportTicketMapper ticketMapper;
    private final SupportMessageMapper messageMapper;
    private final SupportEventMapper supportEventMapper;

    @Override
    public IPage<AgentRunAdminVO> listRuns(Integer pageNum, Integer pageSize,
                                           String status, String promptVersion) {
        QueryWrapper<SupportAgentRun> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (promptVersion != null && !promptVersion.isBlank()) {
            wrapper.eq("prompt_version", promptVersion.trim());
        }
        wrapper.orderByDesc("created_at", "id");

        IPage<SupportAgentRun> page = agentRunMapper.selectPage(
                new Page<>(positivePage(pageNum), positivePageSize(pageSize)),
                wrapper);
        Map<Long, SupportTicket> tickets = loadTickets(page.getRecords().stream()
                .map(SupportAgentRun::getTicketId)
                .distinct()
                .toList());
        return page.convert(run -> toAdminVO(run, tickets.get(run.getTicketId())));
    }

    @Override
    public AgentRunAdminDetailVO detail(Long runId) {
        SupportAgentRun run = requireRun(runId);
        SupportTicket ticket = ticketMapper.selectById(run.getTicketId());
        List<AgentRunStepVO> steps = agentRunStepMapper.selectList(
                        new QueryWrapper<SupportAgentRunStep>()
                                .eq("run_id", runId)
                                .orderByAsc("id"))
                .stream()
                .map(this::toStepVO)
                .toList();
        List<AgentRunEventVO> events = agentRunEventMapper.selectList(
                        new QueryWrapper<SupportAgentRunEvent>()
                                .eq("run_id", runId)
                                .orderByAsc("id"))
                .stream()
                .map(event -> toEventVO(event, run))
                .toList();
        SupportAgentEvaluation evaluation = evaluationMapper.selectOne(
                new QueryWrapper<SupportAgentEvaluation>()
                        .eq("run_id", runId)
                        .last("LIMIT 1"));
        return AgentRunAdminDetailVO.builder()
                .run(toAdminVO(run, ticket))
                .steps(steps)
                .events(events)
                .evaluation(toEvaluationVO(evaluation))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentRunAdminVO takeover(Long runId, Long adminUserId) {
        SupportAgentRun run = requireRun(runId);
        SupportTicket ticket = requireTicket(run.getTicketId());
        Integer fromStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.WAITING_HUMAN.getCode());
        ticket.setAssigneeType(TicketMessageSenderType.HUMAN.name());
        ticket.setAssigneeId(adminUserId);
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        recordTicketEvent(ticket, "HUMAN_TAKEOVER", fromStatus, ticket.getStatus(),
                TicketMessageSenderType.HUMAN.name(), adminUserId, null);
        recordRunEvent(run, "HUMAN_TAKEOVER", "adminUserId=" + adminUserId);
        return toAdminVO(run, ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentRunAdminVO release(Long runId, Long adminUserId) {
        SupportAgentRun run = requireRun(runId);
        SupportTicket ticket = requireTicket(run.getTicketId());
        requireAssignee(ticket, adminUserId);

        Integer fromStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.TRIAGED.getCode());
        ticket.setAssigneeType(null);
        ticket.setAssigneeId(null);
        ticket.setUpdateTime(LocalDateTime.now());
        ticketMapper.updateById(ticket);

        recordTicketEvent(ticket, "HUMAN_RELEASED", fromStatus, ticket.getStatus(),
                TicketMessageSenderType.HUMAN.name(), adminUserId, null);
        recordRunEvent(run, "HUMAN_RELEASED", "adminUserId=" + adminUserId);
        return toAdminVO(run, ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupportMessageVO appendHumanMessage(String ticketNo, Long adminUserId,
                                               CreateSupportMessageDTO dto) {
        SupportTicket ticket = requireTicketByNo(ticketNo);
        if (ticket.getAssigneeId() == null) {
            ticket.setAssigneeType(TicketMessageSenderType.HUMAN.name());
            ticket.setAssigneeId(adminUserId);
        } else {
            requireAssignee(ticket, adminUserId);
        }

        LocalDateTime now = LocalDateTime.now();
        String clientMessageId = blankToNull(dto.getClientMessageId());
        if (clientMessageId != null) {
            SupportMessage existing = messageMapper.selectOne(
                    new QueryWrapper<SupportMessage>()
                            .eq("ticket_id", ticket.getId())
                            .eq("client_message_id", clientMessageId)
                            .last("LIMIT 1"));
            if (existing != null) {
                return toMessageVO(existing, true);
            }
        }

        SupportMessage message = new SupportMessage();
        message.setTicketId(ticket.getId());
        message.setSenderType(TicketMessageSenderType.HUMAN.name());
        message.setSenderId(adminUserId);
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
                SupportMessage existing = messageMapper.selectOne(
                        new QueryWrapper<SupportMessage>()
                                .eq("ticket_id", ticket.getId())
                                .eq("client_message_id", clientMessageId)
                                .last("LIMIT 1"));
                if (existing != null) {
                    return toMessageVO(existing, true);
                }
            }
            throw ex;
        }

        Integer fromStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.WAITING_USER.getCode());
        ticket.setLastMessageAt(now);
        ticket.setUpdateTime(now);
        ticketMapper.updateById(ticket);
        recordTicketEvent(ticket, "HUMAN_MESSAGE_ADDED", fromStatus, ticket.getStatus(),
                TicketMessageSenderType.HUMAN.name(), adminUserId, null);
        return toMessageVO(message, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveTicket(String ticketNo, Long adminUserId) {
        SupportTicket ticket = requireTicketByNo(ticketNo);
        if (ticket.getAssigneeId() != null) {
            requireAssignee(ticket, adminUserId);
        }
        Integer fromStatus = ticket.getStatus();
        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus(TicketStatus.RESOLVED.getCode());
        ticket.setResolvedAt(now);
        ticket.setUpdateTime(now);
        ticketMapper.updateById(ticket);
        recordTicketEvent(ticket, "HUMAN_RESOLVED", fromStatus, ticket.getStatus(),
                TicketMessageSenderType.HUMAN.name(), adminUserId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEvaluationVO evaluate(Long runId, Long adminUserId,
                                      AgentRunEvaluationDTO dto) {
        requireRun(runId);
        SupportAgentEvaluation existing = evaluationMapper.selectOne(
                new QueryWrapper<SupportAgentEvaluation>()
                        .eq("run_id", runId)
                        .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new SupportAgentEvaluation();
            existing.setRunId(runId);
            existing.setEvaluatorId(adminUserId);
            existing.setCreateTime(now);
        }
        existing.setScore(dto.getScore());
        existing.setTags(blankToNull(dto.getTags()));
        existing.setNotes(blankToNull(dto.getNotes()));
        existing.setEvaluatorId(adminUserId);
        existing.setUpdateTime(now);
        if (existing.getId() == null) {
            evaluationMapper.insert(existing);
        } else {
            evaluationMapper.updateById(existing);
        }
        return toEvaluationVO(existing);
    }

    @Override
    public AgentQualitySummaryVO summary(Integer days) {
        int normalizedDays = days == null ? 7 : Math.max(1, Math.min(days, 90));
        LocalDateTime since = LocalDateTime.now().minusDays(normalizedDays);
        List<SupportAgentRun> runs = agentRunMapper.selectList(
                new QueryWrapper<SupportAgentRun>()
                        .ge("created_at", since)
                        .orderByDesc("id")
                        .last("LIMIT 10000"));
        long succeeded = runs.stream()
                .filter(run -> AgentRunStatus.SUCCEEDED.name().equals(run.getStatus()))
                .count();
        long failed = runs.stream()
                .filter(run -> AgentRunStatus.FAILED.name().equals(run.getStatus()))
                .count();
        long active = runs.stream()
                .filter(run -> !isTerminal(run.getStatus()))
                .count();
        long averageDuration = Math.round(runs.stream()
                .filter(run -> run.getDurationMs() != null)
                .mapToLong(SupportAgentRun::getDurationMs)
                .average()
                .orElse(0D));
        long waitingHuman = ticketMapper.selectCount(new QueryWrapper<SupportTicket>()
                .eq("status", TicketStatus.WAITING_HUMAN.getCode()));

        List<Long> runIds = runs.stream().map(SupportAgentRun::getId).toList();
        List<SupportAgentEvaluation> evaluations = runIds.isEmpty()
                ? List.of()
                : evaluationMapper.selectList(new QueryWrapper<SupportAgentEvaluation>()
                        .in("run_id", runIds));
        double averageScore = evaluations.stream()
                .filter(item -> item.getScore() != null)
                .mapToInt(SupportAgentEvaluation::getScore)
                .average()
                .orElse(0D);

        return AgentQualitySummaryVO.builder()
                .days(normalizedDays)
                .totalRuns((long) runs.size())
                .succeededRuns(succeeded)
                .failedRuns(failed)
                .activeRuns(active)
                .waitingHumanTickets(waitingHuman)
                .evaluatedRuns((long) evaluations.size())
                .averageDurationMs(averageDuration)
                .averageScore(Math.round(averageScore * 100D) / 100D)
                .build();
    }

    private Map<Long, SupportTicket> loadTickets(Collection<Long> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SupportTicket> result = new HashMap<>();
        ticketMapper.selectBatchIds(ticketIds).forEach(ticket -> result.put(ticket.getId(), ticket));
        return result;
    }

    private SupportAgentRun requireRun(Long runId) {
        SupportAgentRun run = runId == null ? null : agentRunMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent run not found");
        }
        return run;
    }

    private SupportTicket requireTicket(Long ticketId) {
        SupportTicket ticket = ticketId == null ? null : ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Support ticket not found");
        }
        return ticket;
    }

    private SupportTicket requireTicketByNo(String ticketNo) {
        SupportTicket ticket = ticketMapper.selectOne(new QueryWrapper<SupportTicket>()
                .eq("ticket_no", ticketNo)
                .last("LIMIT 1"));
        if (ticket == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Support ticket not found");
        }
        return ticket;
    }

    private void requireAssignee(SupportTicket ticket, Long adminUserId) {
        if (!adminUserId.equals(ticket.getAssigneeId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Ticket is assigned to another operator");
        }
    }

    private AgentRunAdminVO toAdminVO(SupportAgentRun run, SupportTicket ticket) {
        boolean humanRequired = ticket != null
                && (TicketStatus.WAITING_HUMAN.getCode() == ticket.getStatus()
                || ticket.getAssigneeId() != null);
        return AgentRunAdminVO.builder()
                .runId(run.getId())
                .runNo(run.getRunNo())
                .ticketNo(ticket == null ? null : ticket.getTicketNo())
                .ticketSubject(ticket == null ? null : ticket.getSubject())
                .status(run.getStatus())
                .provider(run.getProvider())
                .model(run.getModel())
                .promptVersion(run.getPromptVersion())
                .humanRequired(humanRequired)
                .assigneeType(ticket == null ? null : ticket.getAssigneeType())
                .assigneeId(ticket == null ? null : ticket.getAssigneeId())
                .errorMessage(run.getErrorMessage())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .durationMs(run.getDurationMs())
                .createTime(run.getCreateTime())
                .build();
    }

    private AgentRunStepVO toStepVO(SupportAgentRunStep step) {
        return AgentRunStepVO.builder()
                .stepId(step.getId())
                .stepName(step.getStepName())
                .status(step.getStatus())
                .errorMessage(step.getErrorMessage())
                .attempt(step.getAttempt())
                .durationMs(step.getDurationMs())
                .startedAt(step.getStartedAt())
                .finishedAt(step.getFinishedAt())
                .build();
    }

    private AgentRunEventVO toEventVO(SupportAgentRunEvent event, SupportAgentRun run) {
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

    private AgentEvaluationVO toEvaluationVO(SupportAgentEvaluation evaluation) {
        if (evaluation == null) {
            return null;
        }
        return AgentEvaluationVO.builder()
                .evaluationId(evaluation.getId())
                .runId(evaluation.getRunId())
                .score(evaluation.getScore())
                .tags(evaluation.getTags())
                .notes(evaluation.getNotes())
                .evaluatorId(evaluation.getEvaluatorId())
                .updateTime(evaluation.getUpdateTime())
                .build();
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

    private void recordTicketEvent(SupportTicket ticket, String eventType, Integer fromStatus,
                                   Integer toStatus, String actorType, Long actorId,
                                   String payload) {
        SupportEvent event = new SupportEvent();
        event.setTicketId(ticket.getId());
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setPayload(payload);
        event.setCreateTime(LocalDateTime.now());
        supportEventMapper.insert(event);
    }

    private void recordRunEvent(SupportAgentRun run, String eventType, String payload) {
        SupportAgentRunEvent event = new SupportAgentRunEvent();
        event.setRunId(run.getId());
        event.setEventType(eventType);
        event.setStatus(run.getStatus());
        event.setLastCompletedStep(run.getLastCompletedStep());
        event.setWorkerId(run.getWorkerId());
        event.setLeaseExpiresAt(run.getLeaseExpiresAt());
        event.setLastHeartbeatAt(run.getLastHeartbeatAt());
        event.setPayload(payload);
        event.setCreateTime(LocalDateTime.now());
        agentRunEventMapper.insert(event);
    }

    private boolean isTerminal(String status) {
        return AgentRunStatus.SUCCEEDED.name().equals(status)
                || AgentRunStatus.FAILED.name().equals(status)
                || AgentRunStatus.CANCELLED.name().equals(status);
    }

    private long positivePage(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    private long positivePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 100);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
