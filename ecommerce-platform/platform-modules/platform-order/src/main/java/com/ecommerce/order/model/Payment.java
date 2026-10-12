package com.ecommerce.order.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单。
 *
 * 为什么不把支付信息直接记在订单上：
 * 订单管「交易」，支付单管「资金」，两者生命周期和状态机不同。
 * 一个订单可能有多笔支付尝试（比如第一次支付超时关闭、用户重新发起），
 * 有了独立支付单才能完整记录每一笔尝试，也才谈得上对账。
 */
@Data
@TableName("od_payment")
public class Payment {

    /** 待支付 */
    public static final int STATUS_PENDING = 0;
    /** 支付成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 已关闭（超时或用户主动取消） */
    public static final int STATUS_CLOSED = 2;

    /** 模拟支付渠道（真实项目这里是支付宝/微信） */
    public static final int CHANNEL_MOCK = 1;

    private Long id;

    /** 支付单号，对外唯一标识 */
    private String paymentNo;

    private Long orderId;

    private Long userId;

    /** 支付金额（下单时的快照，不从订单实时取，防止订单被改） */
    private BigDecimal amount;

    private Integer channel;

    private Integer status;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
