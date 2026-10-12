package com.ecommerce.merchant.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户关注关系。
 * 用一张多态表存商家关注和商品关注，后续要加「关注品类」只扩枚举，不用再加表。
 */
@Data
@TableName("ud_follow")
public class UdFollow {

    /** 关注对象类型：商家 */
    public static final int TYPE_MERCHANT = 1;
    /** 关注对象类型：商品 */
    public static final int TYPE_PRODUCT = 2;

    private Long id;

    /** 关注者用户 ID */
    private Long userId;

    /** 关注对象类型，见 TYPE_* 常量 */
    private Integer targetType;

    /** 关注对象 ID（商家 ID 或商品 ID） */
    private Long targetId;

    @TableField("created_at")
    private LocalDateTime createTime;
}
