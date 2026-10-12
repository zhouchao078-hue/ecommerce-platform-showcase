package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentQualitySummaryVO {
    private Integer days;
    private Long totalRuns;
    private Long succeededRuns;
    private Long failedRuns;
    private Long activeRuns;
    private Long waitingHumanTickets;
    private Long evaluatedRuns;
    private Long averageDurationMs;
    private Double averageScore;
}
