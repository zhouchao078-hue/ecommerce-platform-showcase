package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentEvaluationVO {
    private Long evaluationId;
    private Long runId;
    private Integer score;
    private String tags;
    private String notes;
    private Long evaluatorId;
    private LocalDateTime updateTime;
}
