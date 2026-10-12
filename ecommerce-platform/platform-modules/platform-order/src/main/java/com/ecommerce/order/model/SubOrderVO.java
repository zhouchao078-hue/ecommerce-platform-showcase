package com.ecommerce.order.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 父订单详情里的商家子订单。
 *
 * 每个子订单独立履约，买家可以按店铺分别确认收货。
 */
@Data
public class SubOrderVO {
    private Long id;
    private String orderNo;
    private Long merchantId;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private List<OrderItem> items;
}
