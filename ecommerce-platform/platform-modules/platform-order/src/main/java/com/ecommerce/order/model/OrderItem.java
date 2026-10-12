package com.ecommerce.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("od_order_item")
public class OrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    /**
     * 商家 ID（下单时快照）。
     * 和 productName / productPrice 一样属于快照字段：
     * 商品被删除或改归属后，历史订单仍能追溯是哪家店的。
     */
    private Long merchantId;
    /** SKU ID（下单时快照） */
    private Long skuId;
    /** SKU 名称（下单时快照） */
    private String skuName;
    private String productName;
    private BigDecimal productPrice;
    private String productImage;
    private Integer quantity;
    private BigDecimal subtotal;
}
