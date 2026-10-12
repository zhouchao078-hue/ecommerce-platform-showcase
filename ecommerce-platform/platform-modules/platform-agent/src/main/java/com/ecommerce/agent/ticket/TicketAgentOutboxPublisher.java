package com.ecommerce.agent.ticket;

import com.ecommerce.support.model.SupportOutbox;
import com.ecommerce.support.service.SupportOutboxService;
import com.ecommerce.agent.metrics.AgentMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.ticket", name = "dispatch-mode", havingValue = "rabbit")
public class TicketAgentOutboxPublisher {

    private final SupportOutboxService outboxService;
    private final TicketAgentProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AgentMetrics metrics;

    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        List<SupportOutbox> messages;
        try {
            messages = outboxService.claimPending(
                    20,
                    "RABBIT",
                    properties.getOutboxLeaseSeconds());
        } catch (Exception ex) {
            metrics.recordOutboxPublish("rabbit", "claim_failure");
            log.warn("Failed to claim agent outbox messages", ex);
            return;
        }
        for (SupportOutbox outbox : messages) {
            try {
                String routingKey = resolveRoutingKey(outbox);
                rabbitTemplate.convertAndSend(
                        properties.getRabbitExchange(),
                        routingKey,
                        outbox.getPayload(),
                        message -> {
                            message.getMessageProperties()
                                    .setContentType(MessageProperties.CONTENT_TYPE_JSON);
                            message.getMessageProperties().setDeliveryMode(
                                    MessageDeliveryMode.PERSISTENT);
                            message.getMessageProperties().setMessageId(
                                    "support-outbox:" + outbox.getId());
                            return message;
                        });
                outboxService.markSent(outbox.getId(), outbox.getClaimToken());
                metrics.recordOutboxPublish("rabbit", "success");
            } catch (Exception ex) {
                outboxService.markFailed(
                        outbox.getId(),
                        outbox.getClaimToken(),
                        ex.getMessage());
                metrics.recordOutboxPublish("rabbit", "failure");
                log.warn("Failed to publish agent outbox message: id={}", outbox.getId(), ex);
            }
        }
    }

    private String resolveRoutingKey(SupportOutbox outbox) {
        if ("AGENT_RUN_CANCEL_REQUESTED".equals(outbox.getEventType())) {
            return properties.getRabbitCancelRoutingKey();
        }
        try {
            JsonNode payload = objectMapper.readTree(outbox.getPayload());
            String priority = payload.path("priority").asText("").toLowerCase();
            return switch (priority) {
                case "high", "urgent" -> "agent.run.high";
                case "low" -> "agent.run.low";
                case "normal" -> properties.getRabbitRunRoutingKey();
                default -> properties.getRabbitRunRoutingKey();
            };
        } catch (Exception ex) {
            log.warn("Failed to resolve agent routing key, using default: id={}", outbox.getId());
            return properties.getRabbitRunRoutingKey();
        }
    }
}
