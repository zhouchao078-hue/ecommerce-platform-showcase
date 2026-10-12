package com.ecommerce.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.order.model.AfterSale;
import com.ecommerce.order.service.AfterSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 商家侧售后审核 */
@Slf4j
@RestController
@RequestMapping("/api/merchant/after-sales")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantAfterSaleController {

    private final AfterSaleService afterSaleService;
    private final AuthService authService;

    @GetMapping
    public Result<IPage<AfterSale>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(afterSaleService.listMerchant(
                authService.getCurrentUserId(), status, pageNum, pageSize));
    }

    /** 同意 → 触发退款 */
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        afterSaleService.approve(id, authService.getCurrentUserId());
        return Result.success();
    }

    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        afterSaleService.reject(id, authService.getCurrentUserId(), reason);
        return Result.success();
    }

    /** 确认收到退货 → 退款并回补库存 */
    @PutMapping("/{id}/receive")
    public Result<Void> receiveReturn(@PathVariable Long id) {
        afterSaleService.receiveReturn(id, authService.getCurrentUserId());
        return Result.success();
    }
}
