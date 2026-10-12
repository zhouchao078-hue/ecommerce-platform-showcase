package com.ecommerce.agent.ticket;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketSubmitRequest {
    private String externalTicketId;
    private String externalMessageId;
    private String subject;
    @NotBlank
    private String content;
}
