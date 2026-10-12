package com.ecommerce.agent.ticket;

import com.ecommerce.support.model.SupportOutbox;
import com.ecommerce.support.service.SupportOutboxService;
import com.ecommerce.agent.metrics.AgentMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.ticket", name = "kafka-enabled", havingValue = "true")
public class TicketAgentKafkaPublisher {

    private final SupportOutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TicketAgentProperties properties;
    private final AgentMetrics metrics;

    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        List<SupportOutbox> messages;
        try {
            messages = outboxService.claimPending(
                    20,
                    "KAFKA",
                    properties.getOutboxLeaseSeconds());
        } catch (Exception ex) {
            metrics.recordOutboxPublish("kafka", "claim_failure");
            log.warn("Failed to claim agent Kafka outbox messages", ex);
            return;
        }
        for (SupportOutbox outbox : messages) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        outbox.getDestination(),
                        String.valueOf(outbox.getAggregateId()),
                        outbox.getPayload());
                record.headers().add(
                        "outboxId",
                        String.valueOf(outbox.getId()).getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(record)
                        .get(5, TimeUnit.SECONDS);
                outboxService.markSent(outbox.getId(), outbox.getClaimToken());
                metrics.recordOutboxPublish("kafka", "success");
            } catch (Exception ex) {
                outboxService.markFailed(
                        outbox.getId(),
                        outbox.getClaimToken(),
                        ex.getMessage());
                metrics.recordOutboxPublish("kafka", "failure");
                log.warn("Failed to publish agent Kafka event: id={}", outbox.getId(), ex);
            }
        }
    }
}
