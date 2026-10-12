package com.ecommerce.order.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderListItemVO {
    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer status;
    private String receiverName;
    private String receiverPhone;
    private LocalDateTime createTime;
    private String orderType;
    private String productName;
    private Integer subOrderCount;
}
