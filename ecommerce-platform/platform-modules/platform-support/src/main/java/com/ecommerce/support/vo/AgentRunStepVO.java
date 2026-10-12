package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentRunStepVO {
    private Long stepId;
    private String stepName;
    private String status;
    private String errorMessage;
    private Integer attempt;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
