package com.ecommerce.order.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MerchantRecentOrderVO {
    private Long id;
    private String orderNo;
    private String buyerName;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime createTime;
}
