package com.ecommerce.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品规格 SKU。
 *
 * 商品表上的 price/stock 保存所有启用 SKU 的最低价和总库存，
 * 具体交易、扣库存和售后追溯都落到 SKU 上。
 */
@Data
@TableName("pd_product_sku")
public class PdProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    /** 展示规格，例如「黑色 / M」 */
    private String skuName;
    /** 结构化规格 JSON，后续可扩展颜色、尺码等独立字段 */
    private String specJson;
    private BigDecimal price;
    private Integer stock;
    private String image;
    /** 1=默认规格 */
    private Integer isDefault;
    /** 1=启用 0=停用 */
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createTime;

    @TableField("updated_at")
    private LocalDateTime updateTime;
}
