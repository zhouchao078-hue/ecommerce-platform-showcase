package com.ecommerce.agent.tool;

import com.ecommerce.agent.tool.AgentTool;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.model.OrderSummary;
import com.ecommerce.order.service.OrderCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具：查询当前用户的订单列表
 */
@Component
@RequiredArgsConstructor
public class OrderListTool implements AgentTool {

    private final OrderCacheService orderCacheService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "order_list";
    }

    @Override
    public String getCategory() {
        return "order";
    }

    @Override
    public String getDescription() {
        return "用户询问自己的订单时调用（查订单、看发货状态、订单到哪了），返回最近订单列表（订单号、金额、状态）";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public String execute(Map<String, Object> args, Long userId) {
        if (userId == null) {
            return "用户未登录";
        }
        List<OrderSummary> orders = orderCacheService.listRecentSummaries(userId);
        try {
            List<Map<String, Object>> result = orders.stream()
                    .map(o -> {
                        String statusDesc;
                        try {
                            statusDesc = OrderStatus.fromCode(o.getStatus()).getDesc();
                        } catch (Exception e) {
                            statusDesc = "未知";
                        }
                        return Map.<String, Object>of(
                                "orderNo", o.getOrderNo(),
                                "totalAmount", o.getTotalAmount(),
                                "status", o.getStatus(),
                                "statusDesc", statusDesc,
                                "createTime", String.valueOf(o.getCreateTime()));
                    })
                    .toList();
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "[]";
        }
    }
}
