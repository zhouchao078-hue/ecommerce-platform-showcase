package com.ecommerce.order.controller;

import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.order.model.MerchantDashboardVO;
import com.ecommerce.order.service.MerchantDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantDashboardController {

    private final MerchantDashboardService dashboardService;
    private final AuthService authService;

    @GetMapping
    public Result<MerchantDashboardVO> getDashboard() {
        return Result.success(dashboardService.getDashboard(authService.getCurrentUserId()));
    }
}
