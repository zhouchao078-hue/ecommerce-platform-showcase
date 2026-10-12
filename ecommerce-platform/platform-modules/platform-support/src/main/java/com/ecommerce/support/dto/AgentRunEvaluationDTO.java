package com.ecommerce.support.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentRunEvaluationDTO {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer score;

    @Size(max = 255)
    private String tags;

    @Size(max = 2000)
    private String notes;
}
