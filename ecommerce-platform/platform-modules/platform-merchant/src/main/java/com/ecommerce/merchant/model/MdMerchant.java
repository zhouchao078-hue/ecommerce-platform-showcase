package com.ecommerce.merchant.model;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("md_merchant")
public class MdMerchant {
    /**
     * 商家记录的主键 ID，自增
     */
    private Long id;
    /**
     *用户 ID
     */
    private Long userId;
    /**
     *店铺名称 不能为空
     */
    private String shopName;
    /**
     * 店铺描述 可以为空
     */
    private String description;
    /**
     * 商家状态。0 待审核 → 1 已开通 → 2 已关闭
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
}
