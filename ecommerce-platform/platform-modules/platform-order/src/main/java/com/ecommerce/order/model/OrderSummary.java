package com.ecommerce.order.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 工单 Agent 使用的订单摘要，不包含收货电话和地址。 */
@Data
public class OrderSummary {
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private LocalDateTime createTime;
}
