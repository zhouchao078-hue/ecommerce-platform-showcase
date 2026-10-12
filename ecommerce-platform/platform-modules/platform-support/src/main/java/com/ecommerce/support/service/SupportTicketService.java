package com.ecommerce.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.support.dto.CreateSupportMessageDTO;
import com.ecommerce.support.dto.CreateSupportTicketDTO;
import com.ecommerce.support.vo.SupportTicketDetailVO;
import com.ecommerce.support.vo.SupportTicketVO;
import com.ecommerce.support.vo.SupportMessageVO;
import com.ecommerce.support.vo.AgentRunAcceptedVO;
import com.ecommerce.support.vo.AgentRunEventVO;
import com.ecommerce.support.vo.AgentRunVO;
import com.ecommerce.support.vo.AgentRunCheckpointVO;

import java.util.List;

public interface SupportTicketService {

    SupportTicketVO create(Long userId, CreateSupportTicketDTO dto);

    IPage<SupportTicketVO> listMine(Long userId, Integer pageNum, Integer pageSize);

    SupportTicketDetailVO detail(String ticketNo, Long userId);

    SupportMessageVO appendUserMessage(String ticketNo, Long userId, CreateSupportMessageDTO dto);

    AgentRunAcceptedVO startAgentRun(String ticketNo, String requestPayload, String clientRequestId);

    void markAgentRunRunning(Long runId);

    AgentRunVO getAgentRun(Long runId, Long userId);

    List<AgentRunEventVO> listAgentRunEvents(Long runId, Long userId, Long afterEventId);

    AgentRunVO requestCancelAgentRun(Long runId, Long userId);

    boolean isAgentRunCancelled(Long runId);

    AgentRunVO startAgentRunLease(Long runId, String workerId, int leaseSeconds);

    AgentRunVO heartbeatAgentRun(Long runId, String workerId, int leaseSeconds);

    AgentRunCheckpointVO getAgentRunCheckpoint(Long runId);

    long countActiveAgentRuns(Long userId);

    int recoverExpiredAgentRuns(int batchSize);

    void recordAgentStep(Long runId, String stepName, String status, String inputHash,
                         String outputPayload, String errorMessage, Integer attempt,
                         Long durationMs);

    void finishAgentRun(Long runId, String status, String responsePayload,
                        String errorMessage, Long durationMs);

    boolean claimAgentResult(Long runId);

    void applyAgentResult(Long runId, String ticketNo, String content, String traceId,
                          String structuredPayload, boolean humanRequired);

    void applyClaimedAgentResult(Long runId, String ticketNo, String content, String traceId,
                                 String structuredPayload, boolean humanRequired);
}
