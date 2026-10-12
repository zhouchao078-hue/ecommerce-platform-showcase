package com.ecommerce.cart.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cart")
public class Cart {
    /** 购物车主键ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户ID，关联sys_user表 */
    private Long userId;
    /** 商品ID，关联pd_product表 */
    private Long productId;
    /** SKU ID，关联pd_product_sku表 */
    private Long skuId;
    /** 商品数量 */
    private Integer quantity;
    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createTime;
    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updateTime;
}
