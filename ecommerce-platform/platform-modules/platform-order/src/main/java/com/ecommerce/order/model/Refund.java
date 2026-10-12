package com.ecommerce.order.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款流水。
 * 和支付单同理：资金动作要独立记录，才能对账（什么时候、退了多少、成功没有）。
 */
@Data
@TableName("od_refund")
public class Refund {

    public static final int STATUS_REFUNDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    private Long id;
    private String refundNo;
    private Long afterSaleId;
    private Long orderId;
    private String paymentNo;
    private BigDecimal amount;
    private Integer status;

    @TableField("refunded_at")
    private LocalDateTime refundedAt;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
