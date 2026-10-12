package com.ecommerce.agent.ticket;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.mapper.AfterSaleMapper;
import com.ecommerce.order.mapper.RefundMapper;
import com.ecommerce.order.model.AfterSale;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderVO;
import com.ecommerce.order.model.Refund;
import com.ecommerce.order.model.SubOrderVO;
import com.ecommerce.order.service.OrderCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a read-only support context. PII and write operations are intentionally excluded.
 */
@Service
@RequiredArgsConstructor
public class TicketBusinessContextService {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile(
            "\\bORD[-_]?[A-Za-z0-9-]{3,}\\b",
            Pattern.CASE_INSENSITIVE);

    private final OrderCacheService orderCacheService;
    private final AfterSaleMapper afterSaleMapper;
    private final RefundMapper refundMapper;

    public Map<String, Object> build(Long userId, String content) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("source", "ecommerce-platform");
        context.put("pii_included", false);
        if (userId == null) {
            return context;
        }

        String orderNo = extractOrderNo(content);
        if (orderNo == null) {
            return context;
        }

        Order order = orderCacheService.findByOrderNo(userId, orderNo);
        if (order == null) {
            context.put("order_id", orderNo);
            context.put("found", false);
            return context;
        }

        OrderVO detail = orderCacheService.getOrderDetail(userId, order.getId());
        if (detail == null) {
            context.put("order_id", orderNo);
            context.put("found", false);
            return context;
        }

        context.put("order_id", detail.getOrderNo());
        context.put("found", true);
        context.put("status", statusDescription(detail.getStatus()));
        context.put("status_code", detail.getStatus());
        context.put("total_amount", detail.getTotalAmount());
        context.put("created_at", detail.getCreateTime());
        context.put("order_type", detail.getOrderType());

        List<Map<String, Object>> items = new ArrayList<>();
        Set<Long> relatedOrderIds = new LinkedHashSet<>();
        relatedOrderIds.add(detail.getId());
        if (detail.getItems() != null) {
            detail.getItems().forEach(item -> items.add(toItemContext(item)));
        }
        if (detail.getSubOrders() != null) {
            for (SubOrderVO subOrder : detail.getSubOrders()) {
                relatedOrderIds.add(subOrder.getId());
                if (subOrder.getItems() != null) {
                    subOrder.getItems().forEach(item -> items.add(toItemContext(item)));
                }
            }
        }
        context.put("items", items);

        appendAfterSales(context, relatedOrderIds);
        appendRefunds(context, relatedOrderIds);
        context.put("logistics", Map.of(
                "carrier_tracking_available", false,
                "reason", "Carrier tracking is not integrated yet"));
        return context;
    }

    private Map<String, Object> toItemContext(OrderItem item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("order_item_id", item.getId());
        value.put("product_id", item.getProductId());
        value.put("merchant_id", item.getMerchantId());
        value.put("sku_id", item.getSkuId());
        value.put("sku_name", item.getSkuName());
        value.put("product_name", item.getProductName());
        value.put("product_price", item.getProductPrice());
        value.put("quantity", item.getQuantity());
        value.put("subtotal", item.getSubtotal());
        return value;
    }

    private void appendAfterSales(Map<String, Object> context, Set<Long> orderIds) {
        List<AfterSale> afterSales = afterSaleMapper.selectList(new QueryWrapper<AfterSale>()
                .in("order_id", orderIds)
                .orderByDesc("id"));
        context.put("after_sales", afterSales.stream().map(this::toAfterSaleContext).toList());
    }

    private Map<String, Object> toAfterSaleContext(AfterSale afterSale) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("after_sale_no", afterSale.getAfterSaleNo());
        value.put("order_id", afterSale.getOrderId());
        value.put("order_item_id", afterSale.getOrderItemId());
        value.put("merchant_id", afterSale.getMerchantId());
        value.put("type", afterSale.getType());
        value.put("reason", afterSale.getReason());
        value.put("description", afterSale.getDescription());
        value.put("amount", afterSale.getAmount());
        value.put("status", afterSale.getStatus());
        value.put("reject_reason", afterSale.getRejectReason());
        value.put("return_company", afterSale.getReturnCompany());
        value.put("return_tracking_no", afterSale.getReturnTrackingNo());
        value.put("applied_at", afterSale.getAppliedAt());
        value.put("handled_at", afterSale.getHandledAt());
        return value;
    }

    private void appendRefunds(Map<String, Object> context, Set<Long> orderIds) {
        List<Refund> refunds = refundMapper.selectList(new QueryWrapper<Refund>()
                .in("order_id", orderIds)
                .orderByDesc("id"));
        context.put("refunds", refunds.stream().map(this::toRefundContext).toList());
    }

    private Map<String, Object> toRefundContext(Refund refund) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("refund_no", refund.getRefundNo());
        value.put("after_sale_id", refund.getAfterSaleId());
        value.put("order_id", refund.getOrderId());
        value.put("amount", refund.getAmount());
        value.put("status", refund.getStatus());
        value.put("refunded_at", refund.getRefundedAt());
        value.put("created_at", refund.getCreateTime());
        return value;
    }

    private String extractOrderNo(String content) {
        Matcher matcher = ORDER_NO_PATTERN.matcher(content == null ? "" : content);
        return matcher.find() ? matcher.group().toUpperCase() : null;
    }

    private String statusDescription(Integer status) {
        try {
            return OrderStatus.fromCode(status).getDesc();
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }
}
