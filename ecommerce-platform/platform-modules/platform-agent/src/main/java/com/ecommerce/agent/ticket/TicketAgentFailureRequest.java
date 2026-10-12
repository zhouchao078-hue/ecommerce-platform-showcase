package com.ecommerce.agent.ticket;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketAgentFailureRequest {

    @NotBlank
    private String errorMessage;

    private Long durationMs;
}
