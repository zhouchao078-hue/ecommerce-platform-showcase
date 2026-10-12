package com.ecommerce.agent.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class TicketAgentLeaseRequest {

    @NotBlank
    private String workerId;

    @Min(10)
    private Integer leaseSeconds = 60;
}
