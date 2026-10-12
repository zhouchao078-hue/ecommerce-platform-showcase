package com.ecommerce.api.merchant.vo;

import lombok.Data;

/**
 * 商家主页对外展示的信息。
 * following 为 null 表示未登录（前端据此决定是否显示关注按钮）。
 */
@Data
public class ShopVO {
    private Long id;
    private String shopName;
    private String description;
    /** 在售商品数 */
    private Long productCount;
    /** 关注人数 */
    private Long followerCount;
    /** 当前登录用户是否已关注 */
    private Boolean following;
}
