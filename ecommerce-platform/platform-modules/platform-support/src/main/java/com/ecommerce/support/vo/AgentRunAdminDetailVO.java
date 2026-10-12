package com.ecommerce.support.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentRunAdminDetailVO {
    private AgentRunAdminVO run;
    private List<AgentRunStepVO> steps;
    private List<AgentRunEventVO> events;
    private AgentEvaluationVO evaluation;
}
