package com.ecommerce.order.controller;

import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.order.model.AfterSale;
import com.ecommerce.order.service.AfterSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 买家侧售后 */
@Slf4j
@RestController
@RequestMapping("/api/after-sales")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;
    private final AuthService authService;

    @PostMapping
    public Result<AfterSale> apply(@RequestBody Map<String, Object> body) {
        Long orderItemId = Long.valueOf(String.valueOf(body.get("orderItemId")));
        Integer type = body.get("type") == null
                ? AfterSale.TYPE_REFUND_ONLY
                : Integer.valueOf(String.valueOf(body.get("type")));
        String reason = String.valueOf(body.getOrDefault("reason", ""));
        String description = body.get("description") == null ? null : String.valueOf(body.get("description"));
        BigDecimal amount = body.get("amount") == null ? null : new BigDecimal(String.valueOf(body.get("amount")));
        return Result.success(afterSaleService.apply(
                authService.getCurrentUserId(), orderItemId, type, reason, description, amount));
    }

    @GetMapping
    public Result<List<AfterSale>> listMine() {
        return Result.success(afterSaleService.listMine(authService.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public Result<AfterSale> get(@PathVariable Long id) {
        return Result.success(afterSaleService.getMine(id, authService.getCurrentUserId()));
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        afterSaleService.cancel(id, authService.getCurrentUserId());
        return Result.success();
    }

    @PutMapping("/{id}/return")
    public Result<Void> submitReturn(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String returnCompany = body.get("returnCompany") == null
                ? null : String.valueOf(body.get("returnCompany"));
        String returnTrackingNo = body.get("returnTrackingNo") == null
                ? null : String.valueOf(body.get("returnTrackingNo"));
        afterSaleService.submitReturn(
                id, authService.getCurrentUserId(), returnCompany, returnTrackingNo);
        return Result.success();
    }
}
