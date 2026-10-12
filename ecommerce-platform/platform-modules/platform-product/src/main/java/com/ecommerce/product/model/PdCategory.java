package com.ecommerce.product.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pd_category")
public class PdCategory {
    /**
     * 主键 ID，自增
     */
    private Long id;
    /**
     * 品类名称
     */
    private String name;
    /**
     * 父品类 ID，支持多级分类。比如"手机"的 parentId 是"数码"的 id
     */
    private Long parentId;
    /**
     * 排序
     */
    private int sortOrder;
    /**
     * 商家id
     *
     */
    private Long merchantId;
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
}
