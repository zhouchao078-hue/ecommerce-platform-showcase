package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentRunAcceptedVO {
    private Long runId;
    private String runNo;
    private String ticketNo;
    private String status;
    private boolean reused;
}
