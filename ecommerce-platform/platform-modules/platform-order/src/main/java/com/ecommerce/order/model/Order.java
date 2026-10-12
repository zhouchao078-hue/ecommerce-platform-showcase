package com.ecommerce.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("od_order")
public class Order {

    /** 旧订单或普通独立订单 */
    public static final int TYPE_STANDALONE = 1;
    /** 跨店父订单：承载总金额和一次支付 */
    public static final int TYPE_PARENT = 2;
    /** 商家子订单：承载单店商品和履约状态 */
    public static final int TYPE_CHILD = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 子订单指向父订单；父订单和旧订单为空 */
    private Long parentOrderId;
    private Long userId;
    /** 子订单的商家归属；父订单为空 */
    private Long merchantId;
    private String orderNo;
    private BigDecimal totalAmount;
    /** 1=旧独立订单 2=父订单 3=子订单 */
    private Integer orderType;
    private Integer status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    @TableField("created_at")
    private LocalDateTime createTime;
    @TableField("updated_at")
    private LocalDateTime updateTime;
}
