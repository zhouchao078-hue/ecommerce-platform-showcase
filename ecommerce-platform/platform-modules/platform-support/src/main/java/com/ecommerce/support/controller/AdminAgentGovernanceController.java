package com.ecommerce.support.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.support.dto.AgentRunEvaluationDTO;
import com.ecommerce.support.dto.CreateSupportMessageDTO;
import com.ecommerce.support.service.AgentGovernanceService;
import com.ecommerce.support.vo.AgentEvaluationVO;
import com.ecommerce.support.vo.AgentQualitySummaryVO;
import com.ecommerce.support.vo.AgentRunAdminDetailVO;
import com.ecommerce.support.vo.AgentRunAdminVO;
import com.ecommerce.support.vo.SupportMessageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agent")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAgentGovernanceController {

    private final AgentGovernanceService governanceService;
    private final AuthService authService;

    @GetMapping("/runs")
    public Result<IPage<AgentRunAdminVO>> listRuns(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String promptVersion) {
        return Result.success(governanceService.listRuns(
                pageNum, pageSize, status, promptVersion));
    }

    @GetMapping("/runs/{runId}")
    public Result<AgentRunAdminDetailVO> detail(@PathVariable Long runId) {
        return Result.success(governanceService.detail(runId));
    }

    @PostMapping("/runs/{runId}/takeover")
    public Result<AgentRunAdminVO> takeover(@PathVariable Long runId) {
        return Result.success(governanceService.takeover(
                runId, authService.getCurrentUserId()));
    }

    @PostMapping("/runs/{runId}/release")
    public Result<AgentRunAdminVO> release(@PathVariable Long runId) {
        return Result.success(governanceService.release(
                runId, authService.getCurrentUserId()));
    }

    @PostMapping("/runs/{runId}/evaluations")
    public Result<AgentEvaluationVO> evaluate(
            @PathVariable Long runId,
            @Valid @RequestBody AgentRunEvaluationDTO dto) {
        return Result.success(governanceService.evaluate(
                runId, authService.getCurrentUserId(), dto));
    }

    @PostMapping("/tickets/{ticketNo}/messages")
    public Result<SupportMessageVO> appendHumanMessage(
            @PathVariable String ticketNo,
            @Valid @RequestBody CreateSupportMessageDTO dto) {
        return Result.success(governanceService.appendHumanMessage(
                ticketNo, authService.getCurrentUserId(), dto));
    }

    @PostMapping("/tickets/{ticketNo}/resolve")
    public Result<Void> resolveTicket(@PathVariable String ticketNo) {
        governanceService.resolveTicket(ticketNo, authService.getCurrentUserId());
        return Result.success();
    }

    @GetMapping("/summary")
    public Result<AgentQualitySummaryVO> summary(
            @RequestParam(defaultValue = "7") Integer days) {
        return Result.success(governanceService.summary(days));
    }
}
