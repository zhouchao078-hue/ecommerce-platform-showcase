package com.ecommerce.order.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单。
 *
 * 独立于订单状态机：售后要能被拒绝、被撤销、被平台介入，
 * 还要支持「一个订单里只退其中一件」，塞进订单状态里会让互斥关系变乱。
 */
@Data
@TableName("od_after_sale")
public class AfterSale {

    /** 待审核 */
    public static final int STATUS_PENDING = 0;
    /** 商家已同意：仅退款为退款执行中，退货退款为等待买家寄回 */
    public static final int STATUS_APPROVED = 1;
    /** 商家已拒绝 */
    public static final int STATUS_REJECTED = 2;
    /** 已完成（退款到账） */
    public static final int STATUS_DONE = 3;
    /** 买家已撤销 */
    public static final int STATUS_CANCELLED = 4;
    /** 买家已寄回，等待商家确认收货 */
    public static final int STATUS_RETURNED = 5;

    /** 仅退款 */
    public static final int TYPE_REFUND_ONLY = 1;
    /** 退货退款 */
    public static final int TYPE_RETURN_REFUND = 2;

    private Long id;
    private String afterSaleNo;
    private Long orderId;
    private Long orderItemId;
    private Long userId;
    /** 商家 ID，下单时快照 */
    private Long merchantId;
    private Integer type;
    private String reason;
    private String description;
    private BigDecimal amount;
    private Integer status;
    private String rejectReason;

    /** 退货物流公司 */
    @TableField("return_company")
    private String returnCompany;

    /** 退货物流单号 */
    @TableField("return_tracking_no")
    private String returnTrackingNo;

    @TableField("returned_at")
    private LocalDateTime returnedAt;

    @TableField("received_at")
    private LocalDateTime receivedAt;

    @TableField("applied_at")
    private LocalDateTime appliedAt;

    @TableField("handled_at")
    private LocalDateTime handledAt;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
