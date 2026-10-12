package com.ecommerce.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.api.order.dto.CreateOrderDTO;
import com.ecommerce.common.result.Result;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderVO;
import com.ecommerce.order.model.OrderListItemVO;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ecommerce.auth.service.AuthService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final AuthService authService;
    private final OrderService orderService;

    

    @PostMapping
    public Result<OrderVO> createOrder(@RequestBody @Valid CreateOrderDTO dto) {
        Long userId = authService.getCurrentUserId();
        OrderVO vo = orderService.createOrder(userId, dto.getCartItemIds(), dto.getAddressId(), dto.getRemark());
        return Result.success(vo);
    }

    @GetMapping
    public Result<IPage<OrderListItemVO>> listOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        Long userId = authService.getCurrentUserId();
        return Result.success(orderService.listAllOrders(userId, pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        return Result.success(orderService.getOrderDetail(id, userId));
    }

    @GetMapping("/seckill/{orderNo}")
    public Result<OrderVO> getSeckillOrderDetail(@PathVariable String orderNo) {
        Long userId = authService.getCurrentUserId();
        return Result.success(orderService.getSeckillOrderDetail(orderNo, userId));
    }
    @PutMapping("/{id}/cancel")
    public Result<Void> cancelOrder(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        orderService.cancelOrder(id, userId);
        return Result.success();
    }
    @PostMapping("/{id}/pay")
    public Result<Void> PayOrder(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        orderService.payOrder(id, userId);
        return Result.success();
    }
    @PutMapping("{id}/ship")
    public Result<Void> ShipOrder(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();

        orderService.shipOrder(id, userId);
        return Result.success();
    }
    @PutMapping("{id}/confirm")
    public Result<Void> ConfirmOrder(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        orderService.conFirmOrder(id, userId);
        return Result.success();
    }

}
