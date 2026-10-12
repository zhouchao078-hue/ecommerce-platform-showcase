package com.ecommerce.agent.ticket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketAgentCallbackRequest {

    @NotBlank
    private String ticketNo;

    @Valid
    @NotNull
    private TicketAgentResponse result;

    private Long durationMs;

    private String errorMessage;
}
