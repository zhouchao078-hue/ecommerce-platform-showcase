package com.ecommerce.agent.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketFollowUpRequest {

    @NotBlank
    @Size(max = 5000)
    private String content;

    @Size(max = 64)
    private String externalMessageId;
}
