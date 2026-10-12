package com.ecommerce.agent.ticket;

import com.ecommerce.common.result.Result;
import com.ecommerce.support.vo.AgentRunAcceptedVO;
import com.ecommerce.support.vo.AgentRunVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ticket-agent")
@RequiredArgsConstructor
public class TicketAgentController {
    private final TicketAgentClient ticketAgentClient;

    @PostMapping("/tickets")
    public Result<AgentRunAcceptedVO> submit(@Valid @RequestBody TicketSubmitRequest request) {
        return Result.success(ticketAgentClient.submit(request));
    }

    @PostMapping("/tickets/{ticketNo}/messages")
    public Result<AgentRunAcceptedVO> followUp(
            @PathVariable String ticketNo,
            @Valid @RequestBody TicketFollowUpRequest request) {
        return Result.success(ticketAgentClient.followUp(ticketNo, request));
    }

    @GetMapping("/runs/{runId}")
    public Result<AgentRunVO> getRun(@PathVariable Long runId) {
        return Result.success(ticketAgentClient.getRun(runId));
    }

    @PostMapping("/runs/{runId}/cancel")
    public Result<AgentRunVO> cancelRun(@PathVariable Long runId) {
        return Result.success(ticketAgentClient.cancelRun(runId));
    }
}
