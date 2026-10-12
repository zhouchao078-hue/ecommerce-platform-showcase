package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentRunEventVO {
    private Long eventId;
    private String eventType;
    private Long runId;
    private String runNo;
    private String status;
    private String lastCompletedStep;
    private String workerId;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime lastHeartbeatAt;
    private String errorMessage;
    private LocalDateTime occurredAt;
}
