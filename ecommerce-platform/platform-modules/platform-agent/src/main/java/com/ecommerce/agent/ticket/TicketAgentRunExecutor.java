package com.ecommerce.agent.ticket;

import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.support.enums.AgentRunStatus;
import com.ecommerce.support.enums.AgentStepStatus;
import com.ecommerce.support.service.SupportTicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class TicketAgentRunExecutor {

    private final TicketAgentProperties properties;
    private final SupportTicketService supportTicketService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Map<Long, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public TicketAgentRunExecutor(TicketAgentProperties properties,
                                  SupportTicketService supportTicketService,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.supportTicketService = supportTicketService;
        this.objectMapper = objectMapper;
        AtomicInteger sequence = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                Math.max(1, properties.getMaxConcurrentRuns()),
                Math.max(1, properties.getMaxConcurrentRuns()),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.getMaxQueuedRuns())),
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "ticket-agent-run-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void submit(Long runId, String ticketNo, Map<String, Object> payload) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            execute(runId, ticketNo, payload);
            return null;
        });
        runningTasks.put(runId, task);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            runningTasks.remove(runId);
            task.cancel(true);
            supportTicketService.finishAgentRun(
                    runId,
                    AgentRunStatus.FAILED.name(),
                    null,
                    "Ticket agent queue is full",
                    0L);
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "Ticket agent queue is full");
        }
    }

    public void cancel(Long runId) {
        Future<?> task = runningTasks.remove(runId);
        if (task != null) {
            task.cancel(true);
        }
        cancelRemote(runId);
        supportTicketService.finishAgentRun(
                runId,
                AgentRunStatus.CANCELLED.name(),
                null,
                "Cancelled by user",
                0L);
    }

    private void execute(Long runId, String ticketNo, Map<String, Object> payload) {
        long startedAt = System.currentTimeMillis();
        try {
            if (supportTicketService.isAgentRunCancelled(runId)) {
                finishCancelled(runId, startedAt);
                return;
            }
            supportTicketService.markAgentRunRunning(runId);
            supportTicketService.recordAgentStep(
                    runId,
                    "REQUEST_PREPARED",
                    AgentStepStatus.SUCCEEDED.name(),
                    sha256(writeJson(payload)),
                    null,
                    null,
                    1,
                    0L);

            WebClient client = WebClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .build();
            TicketAgentResponse submission = client
                    .post()
                    .uri("/api/v1/ticket-events")
                    .header("X-Internal-Token", properties.getInternalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(TicketAgentResponse.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();
            if (submission == null || submission.getTicketId() == null) {
                throw new IllegalStateException("Ticket agent returned an empty ticket");
            }
            if (supportTicketService.isAgentRunCancelled(runId)) {
                finishCancelled(runId, startedAt);
                return;
            }

            TicketAgentResponse response = client.get()
                    .uri("/api/v1/tickets/{ticketId}", submission.getTicketId())
                    .header("X-Internal-Token", properties.getInternalToken())
                    .retrieve()
                    .bodyToMono(TicketAgentResponse.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();
            if (response == null) {
                throw new IllegalStateException("Ticket agent returned an empty response");
            }
            if (supportTicketService.isAgentRunCancelled(runId)) {
                finishCancelled(runId, startedAt);
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
                    System.currentTimeMillis() - startedAt);
            supportTicketService.recordAgentStep(
                    runId,
                    "GUARDRAIL_VALIDATED",
                    AgentStepStatus.SUCCEEDED.name(),
                    null,
                    writeJson(response.getValidationResult()),
                    null,
                    1,
                    0L);

            supportTicketService.finishAgentRun(
                    runId,
                    AgentRunStatus.SUCCEEDED.name(),
                    writeJson(response),
                    null,
                    System.currentTimeMillis() - startedAt);
            supportTicketService.applyAgentResult(
                    runId,
                    ticketNo,
                    responseText(response),
                    response.getTraceId(),
                    writeJson(response),
                    Boolean.TRUE.equals(response.getHumanRequired()));
        } catch (Exception ex) {
            if (supportTicketService.isAgentRunCancelled(runId)) {
                finishCancelled(runId, startedAt);
            } else {
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                supportTicketService.recordAgentStep(
                        runId,
                        "RUN_FAILED",
                        AgentStepStatus.FAILED.name(),
                        null,
                        null,
                        message,
                        1,
                        System.currentTimeMillis() - startedAt);
                supportTicketService.finishAgentRun(
                        runId,
                        AgentRunStatus.FAILED.name(),
                        null,
                        message,
                        System.currentTimeMillis() - startedAt);
                log.warn("Ticket agent run failed: runId={}, reason={}", runId, message);
            }
        } finally {
            runningTasks.remove(runId);
        }
    }

    private void finishCancelled(Long runId, long startedAt) {
        supportTicketService.finishAgentRun(
                runId,
                AgentRunStatus.CANCELLED.name(),
                null,
                "Cancelled by user",
                System.currentTimeMillis() - startedAt);
    }

    private void cancelRemote(Long runId) {
        try {
            WebClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .build()
                    .post()
                    .uri("/api/v1/agent-runs/{runId}/cancel", runId)
                    .header("X-Internal-Token", properties.getInternalToken())
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(2))
                    .block();
        } catch (Exception ex) {
            log.debug("Remote ticket agent cancellation was not acknowledged: runId={}", runId);
        }
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

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
