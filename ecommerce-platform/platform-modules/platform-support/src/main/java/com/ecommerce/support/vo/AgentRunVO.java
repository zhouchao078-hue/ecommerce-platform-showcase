package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentRunVO {
    private Long runId;
    private String runNo;
    private String ticketNo;
    private String status;
    private Boolean cancelRequested;
    private String lastCompletedStep;
    private String workerId;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime lastHeartbeatAt;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
}
