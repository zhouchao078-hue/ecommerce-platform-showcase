package com.ecommerce.agent.ticket;

import com.ecommerce.agent.config.AgentExecutionProperties;
import com.ecommerce.agent.metrics.AgentMetrics;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.support.service.SupportTicketService;
import com.ecommerce.support.vo.AgentRunEventVO;
import com.ecommerce.support.vo.AgentRunVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/api/ticket-agent/runs")
@RequiredArgsConstructor
public class TicketAgentStreamController {

    private static final long POLL_MILLIS = 1000L;

    private final AuthService authService;
    private final SupportTicketService supportTicketService;
    private final ScheduledExecutorService ticketSseScheduler;
    private final AgentExecutionProperties executionProperties;
    private final AgentMetrics agentMetrics;

    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable Long runId,
            @RequestHeader(value = "Last-Event-ID", required = false) Long headerEventId,
            @RequestParam(value = "lastEventId", required = false) Long queryEventId) {
        Long userId = authService.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(
                Duration.ofSeconds(executionProperties.getSseTimeoutSeconds()).toMillis());
        agentMetrics.recordSseConnectionOpened();
        AtomicBoolean metricsActive = new AtomicBoolean(true);
        Runnable closeMetrics = () -> {
            if (metricsActive.compareAndSet(true, false)) {
                agentMetrics.recordSseConnectionClosed();
            }
        };
        Long initialEventId = queryEventId != null ? queryEventId : headerEventId;
        AtomicLong lastEventId = new AtomicLong(initialEventId == null ? 0L : Math.max(0L, initialEventId));
        AtomicBoolean initialSnapshotSent = new AtomicBoolean(false);
        AtomicReference<Long> lastHeartbeatAt = new AtomicReference<>(System.currentTimeMillis());
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

        Runnable poll = () -> {
            if (closed.get()) {
                return;
            }
            try {
                long now = System.currentTimeMillis();
                List<AgentRunEventVO> events = supportTicketService.listAgentRunEvents(
                        runId, userId, lastEventId.get());
                if (!events.isEmpty()) {
                    initialSnapshotSent.set(true);
                    for (AgentRunEventVO event : events) {
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(event.getEventId()))
                                .name("run.status")
                                .data(event, MediaType.APPLICATION_JSON));
                        lastEventId.set(event.getEventId());
                    }
                    lastHeartbeatAt.set(now);
                    if (isTerminal(events.get(events.size() - 1).getStatus())) {
                        closed.set(true);
                        closeMetrics.run();
                        emitter.complete();
                        return;
                    }
                } else if (!initialSnapshotSent.get()) {
                    AgentRunVO run = supportTicketService.getAgentRun(runId, userId);
                    initialSnapshotSent.set(true);
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(lastEventId.get()))
                            .name("run.status")
                            .data(toSnapshot(run), MediaType.APPLICATION_JSON));
                    lastHeartbeatAt.set(now);
                    if (isTerminal(run.getStatus())) {
                        closed.set(true);
                        closeMetrics.run();
                        emitter.complete();
                        return;
                    }
                } else if (now - lastHeartbeatAt.get() >= executionProperties.getSseHeartbeatMillis()) {
                    emitter.send(SseEmitter.event()
                            .name("ping")
                            .data(Map.of("timestamp", LocalDateTime.now().toString()),
                                    MediaType.APPLICATION_JSON));
                    lastHeartbeatAt.set(now);
                }
            } catch (Exception ex) {
                if (!closed.getAndSet(true)) {
                    ScheduledFuture<?> future = scheduledFuture.get();
                    if (future != null) {
                        future.cancel(true);
                    }
                    if (isClientDisconnect(ex)) {
                        closeMetrics.run();
                        log.debug("SSE client disconnected: runId={}", runId);
                        return;
                    }
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("message", safeMessage(ex)),
                                        MediaType.APPLICATION_JSON));
                    } catch (Exception ignored) {
                        // Client disconnected.
                    }
                    closeMetrics.run();
                    emitter.completeWithError(ex);
                }
            }
        };

        ScheduledFuture<?> future = ticketSseScheduler.scheduleAtFixedRate(
                poll,
                0,
                POLL_MILLIS,
                TimeUnit.MILLISECONDS);
        scheduledFuture.set(future);
        emitter.onCompletion(() -> {
            closed.set(true);
            closeMetrics.run();
            future.cancel(true);
        });
        emitter.onTimeout(() -> {
            closed.set(true);
            closeMetrics.run();
            future.cancel(true);
            emitter.complete();
        });
        emitter.onError(ex -> {
            closed.set(true);
            closeMetrics.run();
            future.cancel(true);
        });
        return emitter;
    }

    private AgentRunEventVO toSnapshot(AgentRunVO run) {
        return AgentRunEventVO.builder()
                .eventType("SNAPSHOT")
                .runId(run.getRunId())
                .runNo(run.getRunNo())
                .status(run.getStatus())
                .lastCompletedStep(run.getLastCompletedStep())
                .workerId(run.getWorkerId())
                .leaseExpiresAt(run.getLeaseExpiresAt())
                .lastHeartbeatAt(run.getLastHeartbeatAt())
                .errorMessage(run.getErrorMessage())
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status)
                || "FAILED".equals(status)
                || "CANCELLED".equals(status)
                || "TIMED_OUT".equals(status);
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            if (current instanceof IOException
                    || name.contains("AsyncRequestNotUsableException")
                    || name.contains("ClientAbortException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
