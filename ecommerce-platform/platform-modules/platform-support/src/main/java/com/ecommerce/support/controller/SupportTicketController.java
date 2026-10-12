package com.ecommerce.support.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.support.dto.CreateSupportMessageDTO;
import com.ecommerce.support.dto.CreateSupportTicketDTO;
import com.ecommerce.support.service.SupportTicketService;
import com.ecommerce.support.vo.SupportTicketDetailVO;
import com.ecommerce.support.vo.SupportTicketVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final AuthService authService;

    @PostMapping
    public Result<SupportTicketVO> create(@Valid @RequestBody CreateSupportTicketDTO dto) {
        return Result.success(supportTicketService.create(authService.getCurrentUserId(), dto));
    }

    @GetMapping
    public Result<IPage<SupportTicketVO>> listMine(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(supportTicketService.listMine(
                authService.getCurrentUserId(), pageNum, pageSize));
    }

    @GetMapping("/{ticketNo}")
    public Result<SupportTicketDetailVO> detail(@PathVariable String ticketNo) {
        return Result.success(supportTicketService.detail(ticketNo, authService.getCurrentUserId()));
    }

    @PostMapping("/{ticketNo}/messages")
    public Result<Void> appendMessage(
            @PathVariable String ticketNo,
            @Valid @RequestBody CreateSupportMessageDTO dto) {
        supportTicketService.appendUserMessage(ticketNo, authService.getCurrentUserId(), dto);
        return Result.success();
    }
}
