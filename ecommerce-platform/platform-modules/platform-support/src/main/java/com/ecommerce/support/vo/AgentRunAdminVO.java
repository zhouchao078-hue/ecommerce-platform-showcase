package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentRunAdminVO {
    private Long runId;
    private String runNo;
    private String ticketNo;
    private String ticketSubject;
    private String status;
    private String provider;
    private String model;
    private String promptVersion;
    private Boolean humanRequired;
    private String assigneeType;
    private Long assigneeId;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private LocalDateTime createTime;
}
