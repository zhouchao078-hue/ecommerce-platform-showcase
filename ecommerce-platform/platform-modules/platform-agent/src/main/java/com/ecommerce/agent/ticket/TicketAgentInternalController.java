package com.ecommerce.agent.ticket;

import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.common.result.Result;
import com.ecommerce.support.vo.AgentRunCheckpointVO;
import com.ecommerce.support.vo.AgentRunVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/internal/ticket-agent")
@RequiredArgsConstructor
public class TicketAgentInternalController {

    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final TicketAgentProperties properties;
    private final TicketAgentResultService resultService;
    private final com.ecommerce.support.service.SupportTicketService supportTicketService;

    @PostMapping("/runs/{runId}/start")
    public Result<AgentRunVO> startLease(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable Long runId,
            @Valid @RequestBody TicketAgentLeaseRequest request) {
        verifyToken(token);
        return Result.success(supportTicketService.startAgentRunLease(
                runId, request.getWorkerId(), request.getLeaseSeconds()));
    }

    @PostMapping("/runs/{runId}/heartbeat")
    public Result<AgentRunVO> heartbeat(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable Long runId,
            @Valid @RequestBody TicketAgentLeaseRequest request) {
        verifyToken(token);
        return Result.success(supportTicketService.heartbeatAgentRun(
                runId, request.getWorkerId(), request.getLeaseSeconds()));
    }

    @GetMapping("/runs/{runId}/checkpoint")
    public Result<AgentRunCheckpointVO> checkpoint(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable Long runId) {
        verifyToken(token);
        return Result.success(supportTicketService.getAgentRunCheckpoint(runId));
    }

    @PostMapping("/runs/{runId}/result")
    public Result<Void> complete(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable Long runId,
            @Valid @RequestBody TicketAgentCallbackRequest request) {
        verifyToken(token);
        resultService.complete(
                runId,
                request.getTicketNo(),
                request.getResult(),
                request.getDurationMs());
        return Result.success();
    }

    @PostMapping("/runs/{runId}/failure")
    public Result<Void> fail(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable Long runId,
            @Valid @RequestBody TicketAgentFailureRequest request) {
        verifyToken(token);
        resultService.fail(runId, request.getErrorMessage(), request.getDurationMs());
        return Result.success();
    }

    private void verifyToken(String token) {
        byte[] expected = properties.getInternalToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Invalid internal token");
        }
    }
}
