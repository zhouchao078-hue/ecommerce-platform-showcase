package com.ecommerce.order.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消定时任务
 * <p>
 * 待付款订单超过 30 分钟未支付，由系统自动取消并回补库存。
 * 每 60 秒扫描一次；通过状态判断保证幂等（已取消/已支付的订单不会被重复处理）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    /** 超时时间（分钟） */
    private static final int TIMEOUT_MINUTES = 30;

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    /**
     * 每 60 秒扫描一次超时订单
     */
    @Scheduled(fixedDelay = 60000)
    public void cancelTimeoutOrders() {
        try {
            // 1. 查出所有超时未付款的订单：status=0(待付款) 且 created_at < now-30min
            LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
            QueryWrapper<Order> wrapper = new QueryWrapper<>();
            wrapper.eq("status", OrderStatus.PENDING_PAYMENT.getCode())
                    .isNull("parent_order_id")
                    .lt("created_at", deadline);
            List<Order> orders = orderMapper.selectList(wrapper);
            if (orders.isEmpty()) {
                return;
            }
            log.info("订单超时任务：发现 {} 个待取消订单", orders.size());

            // 2. 逐个取消（幂等：cancelTimeoutOrder 内部只处理待付款状态）
            for (Order order : orders) {
                try {
                    orderService.cancelTimeoutOrder(order.getId());
                    log.info("超时订单已自动取消: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
                } catch (Exception e) {
                    // 单个订单失败不影响其他订单
                    log.error("超时订单取消失败: orderId={}, 原因={}", order.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("订单超时任务执行异常: {}", e.getMessage());
        }
    }
}
