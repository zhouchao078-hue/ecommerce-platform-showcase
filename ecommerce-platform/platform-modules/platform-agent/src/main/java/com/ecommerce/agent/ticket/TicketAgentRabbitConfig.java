package com.ecommerce.agent.ticket;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "agent.ticket", name = "dispatch-mode", havingValue = "rabbit")
public class TicketAgentRabbitConfig {

    @Bean
    public TopicExchange supportAgentExchange(TicketAgentProperties properties) {
        return new TopicExchange(properties.getRabbitExchange(), true, false);
    }

    @Bean
    public Queue supportAgentRunHighQueue() {
        return new Queue("support.agent.run.high", true);
    }

    @Bean
    public Queue supportAgentRunNormalQueue() {
        return new Queue("support.agent.run.normal", true);
    }

    @Bean
    public Queue supportAgentRunLowQueue() {
        return new Queue("support.agent.run.low", true);
    }

    @Bean
    public Queue supportAgentRunRetryQueue() {
        return new Queue("support.agent.run.retry.30s", true);
    }

    @Bean
    public Queue supportAgentRunDeadQueue() {
        return new Queue("support.agent.run.dlq", true);
    }

    @Bean
    public Queue supportAgentCancelQueue() {
        return new Queue("support.agent.cancel", true);
    }

    @Bean
    public Binding supportAgentHighBinding(TopicExchange supportAgentExchange,
                                           Queue supportAgentRunHighQueue) {
        return BindingBuilder.bind(supportAgentRunHighQueue)
                .to(supportAgentExchange)
                .with("agent.run.high");
    }

    @Bean
    public Binding supportAgentNormalBinding(TopicExchange supportAgentExchange,
                                             Queue supportAgentRunNormalQueue,
                                             TicketAgentProperties properties) {
        return BindingBuilder.bind(supportAgentRunNormalQueue)
                .to(supportAgentExchange)
                .with(properties.getRabbitRunRoutingKey());
    }

    @Bean
    public Binding supportAgentLowBinding(TopicExchange supportAgentExchange,
                                          Queue supportAgentRunLowQueue) {
        return BindingBuilder.bind(supportAgentRunLowQueue)
                .to(supportAgentExchange)
                .with("agent.run.low");
    }

    @Bean
    public Binding supportAgentRetryBinding(TopicExchange supportAgentExchange,
                                            Queue supportAgentRunRetryQueue) {
        return BindingBuilder.bind(supportAgentRunRetryQueue)
                .to(supportAgentExchange)
                .with("agent.run.retry");
    }

    @Bean
    public Binding supportAgentDeadBinding(TopicExchange supportAgentExchange,
                                           Queue supportAgentRunDeadQueue) {
        return BindingBuilder.bind(supportAgentRunDeadQueue)
                .to(supportAgentExchange)
                .with("agent.run.dlq");
    }

    @Bean
    public Binding supportAgentCancelBinding(TopicExchange supportAgentExchange,
                                             Queue supportAgentCancelQueue,
                                             TicketAgentProperties properties) {
        return BindingBuilder.bind(supportAgentCancelQueue)
                .to(supportAgentExchange)
                .with(properties.getRabbitCancelRoutingKey());
    }
}
