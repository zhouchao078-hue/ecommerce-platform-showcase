package com.ecommerce.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSupportMessageDTO {

    @NotBlank
    @Size(max = 5000)
    private String content;

    @Size(max = 32)
    private String messageType;

    @Size(max = 64)
    private String traceId;

    @Size(max = 64)
    private String clientMessageId;
}
