package com.ecommerce.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家侧订单管理。
 * 和买家侧的 /api/orders 分开：买家看「我买的」，商家看「我卖的」，
 * 两者的权限判据和可见字段都不一样，混在一个 Controller 里迟早出问题。
 */
@Slf4j
@RestController
@RequestMapping("/api/merchant/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantOrderController {

    private final OrderService orderService;
    private final AuthService authService;

    /** 我店铺的订单（含买家收货信息，商家发货需要） */
    @GetMapping
    public Result<IPage<Order>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(orderService.listMerchantOrders(
                authService.getCurrentUserId(), pageNum, pageSize, status));
    }

    /** 发货：待发货 → 待收货 */
    @PutMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        orderService.shipOrder(id, authService.getCurrentUserId());
        return Result.success();
    }
}
