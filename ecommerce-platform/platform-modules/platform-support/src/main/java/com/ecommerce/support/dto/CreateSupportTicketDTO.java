package com.ecommerce.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSupportTicketDTO {

    @NotBlank
    @Size(max = 200)
    private String subject;

    @NotBlank
    @Size(max = 5000)
    private String content;

    @Size(max = 32)
    private String channel;

    @Size(max = 80)
    private String category;

    @Size(max = 64)
    private String clientRequestId;
}
