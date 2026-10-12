package com.ecommerce.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ecommerce.support.enums.AgentRunStatus;
import com.ecommerce.support.mapper.SupportAgentRunMapper;
import com.ecommerce.support.mapper.SupportAgentRunEventMapper;
import com.ecommerce.support.mapper.SupportAgentRunStepMapper;
import com.ecommerce.support.mapper.SupportEventMapper;
import com.ecommerce.support.mapper.SupportMessageMapper;
import com.ecommerce.support.mapper.SupportTicketMapper;
import com.ecommerce.support.model.SupportAgentRun;
import com.ecommerce.support.model.SupportMessage;
import com.ecommerce.support.model.SupportTicket;
import com.ecommerce.support.service.SupportOutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceImplTest {

    @Mock
    private SupportTicketMapper ticketMapper;
    @Mock
    private SupportMessageMapper messageMapper;
    @Mock
    private SupportEventMapper eventMapper;
    @Mock
    private SupportAgentRunMapper agentRunMapper;
    @Mock
    private SupportAgentRunEventMapper agentRunEventMapper;
    @Mock
    private SupportAgentRunStepMapper agentRunStepMapper;
    @Mock
    private SupportOutboxService supportOutboxService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SupportTicketServiceImpl service;

    @Test
    void applyAgentResultSkipsWhenAnotherCallbackAlreadyClaimedPersistence() {
        when(agentRunMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        service.applyAgentResult(
                11L,
                "SP-TEST-1",
                "Agent reply",
                "trace-1",
                "{}",
                false);

        verify(messageMapper, never()).insert(any(SupportMessage.class));
        verify(ticketMapper, never()).selectOne(any());
    }

    @Test
    void cancelRunEnqueuesRabbitCancellationWhenRunningInRabbitMode() throws Exception {
        ReflectionTestUtils.setField(service, "dispatchMode", "rabbit");
        SupportAgentRun pending = new SupportAgentRun();
        pending.setId(11L);
        pending.setRunNo("AR-11");
        pending.setTicketId(21L);
        pending.setStatus(AgentRunStatus.PENDING.name());
        pending.setCancelRequested(false);
        SupportAgentRun cancelled = new SupportAgentRun();
        cancelled.setId(11L);
        cancelled.setRunNo("AR-11");
        cancelled.setTicketId(21L);
        cancelled.setStatus(AgentRunStatus.CANCEL_REQUESTED.name());
        cancelled.setCancelRequested(true);
        SupportTicket ticket = new SupportTicket();
        ticket.setId(21L);
        ticket.setTicketNo("SP-21");
        ticket.setUserId(1L);

        when(agentRunMapper.selectById(11L)).thenReturn(pending, cancelled);
        when(agentRunMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(ticketMapper.selectById(21L)).thenReturn(ticket);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.requestCancelAgentRun(11L, 1L);

        verify(supportOutboxService).enqueue(
                eq("AGENT_RUN"),
                eq(11L),
                eq("AGENT_RUN_CANCEL_REQUESTED"),
                eq("RABBIT"),
                eq("support.agent.exchange"),
                anyString());
    }

    @Test
    void recoverExpiredRunRequeuesTheOriginalRequest() throws Exception {
        ReflectionTestUtils.setField(service, "dispatchMode", "rabbit");
        SupportAgentRun expired = new SupportAgentRun();
        expired.setId(12L);
        expired.setRunNo("AR-12");
        expired.setTicketId(22L);
        expired.setStatus(AgentRunStatus.RUNNING.name());
        expired.setCancelRequested(false);
        expired.setWorkerId("dead-worker");
        expired.setLeaseExpiresAt(LocalDateTime.now().minusMinutes(5));
        expired.setRequestPayload("{\"content\":\"help\"}");
        SupportTicket ticket = new SupportTicket();
        ticket.setId(22L);
        ticket.setTicketNo("SP-22");
        ticket.setPriority("HIGH");

        when(agentRunMapper.selectList(any())).thenReturn(List.of(expired));
        when(agentRunMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(ticketMapper.selectById(22L)).thenReturn(ticket);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        int recovered = service.recoverExpiredAgentRuns(10);

        assertEquals(1, recovered);
        verify(supportOutboxService).enqueue(
                eq("AGENT_RUN"),
                eq(12L),
                eq("AGENT_RUN_REQUESTED"),
                eq("RABBIT"),
                eq("support.agent.exchange"),
                anyString());
    }

    @Test
    void markRunningPublishesKafkaStartedEventWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(service, "kafkaEnabled", true);
        ReflectionTestUtils.setField(service, "kafkaRunTopic", "support.agent.run.events.v1");
        SupportAgentRun running = new SupportAgentRun();
        running.setId(13L);
        running.setRunNo("AR-13");
        running.setTicketId(23L);
        running.setStatus(AgentRunStatus.RUNNING.name());
        SupportTicket ticket = new SupportTicket();
        ticket.setId(23L);
        ticket.setTicketNo("SP-23");
        ticket.setPriority("NORMAL");

        when(agentRunMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(agentRunMapper.selectById(13L)).thenReturn(running);
        when(ticketMapper.selectById(23L)).thenReturn(ticket);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.markAgentRunRunning(13L);

        verify(supportOutboxService).enqueue(
                eq("AGENT_RUN"),
                eq(13L),
                eq("AGENT_RUN_STARTED"),
                eq("KAFKA"),
                eq("support.agent.run.events.v1"),
                anyString());
    }
}
