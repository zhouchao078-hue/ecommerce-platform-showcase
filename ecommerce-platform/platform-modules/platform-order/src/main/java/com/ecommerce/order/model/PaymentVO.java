package com.ecommerce.order.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentVO {
    private String paymentNo;
    private Long orderId;
    private BigDecimal amount;
    private Integer status;
    private String statusDesc;
    private LocalDateTime paidAt;
}
