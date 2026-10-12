package com.ecommerce.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.support.dto.AgentRunEvaluationDTO;
import com.ecommerce.support.dto.CreateSupportMessageDTO;
import com.ecommerce.support.vo.AgentEvaluationVO;
import com.ecommerce.support.vo.AgentQualitySummaryVO;
import com.ecommerce.support.vo.AgentRunAdminDetailVO;
import com.ecommerce.support.vo.AgentRunAdminVO;
import com.ecommerce.support.vo.SupportMessageVO;

public interface AgentGovernanceService {

    IPage<AgentRunAdminVO> listRuns(Integer pageNum, Integer pageSize,
                                    String status, String promptVersion);

    AgentRunAdminDetailVO detail(Long runId);

    AgentRunAdminVO takeover(Long runId, Long adminUserId);

    AgentRunAdminVO release(Long runId, Long adminUserId);

    SupportMessageVO appendHumanMessage(String ticketNo, Long adminUserId,
                                        CreateSupportMessageDTO dto);

    void resolveTicket(String ticketNo, Long adminUserId);

    AgentEvaluationVO evaluate(Long runId, Long adminUserId, AgentRunEvaluationDTO dto);

    AgentQualitySummaryVO summary(Integer days);
}
