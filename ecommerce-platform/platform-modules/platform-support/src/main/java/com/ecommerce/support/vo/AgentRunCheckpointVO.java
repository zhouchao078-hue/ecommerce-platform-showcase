package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentRunCheckpointVO {
    private String status;
    private Boolean cancelRequested;
    private String lastCompletedStep;
    private String modelResponsePayload;
}
