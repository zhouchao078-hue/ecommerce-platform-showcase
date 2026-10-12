package com.ecommerce.product.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("pd_product")
public class PdProduct {
    /**
     * 主键 ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 商家 ID
     */
    private Long merchantId;
    /**
     * 品类 ID
     */
    private Long categoryId;
    /**
     * 商品名称
     */
    private String name;
    /**
     * 商品描述
     */
    private String description;
    /**
     * 库存数量
     */
    private Integer stock;
    /**
     * 价格
     */
    private BigDecimal price;
    /**
     * 图片
     */
    private String images;
    /**
     * 商品状态 0：下架 ， 1：上架
     */
    private Integer status;
    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updateTime;

    /** 商品详情返回 SKU 列表；普通列表不填充 */
    @TableField(exist = false)
    private List<PdProductSku> skus;
}
