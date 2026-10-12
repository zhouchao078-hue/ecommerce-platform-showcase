package com.ecommerce.user.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知。
 *
 * 一期只做站内（打开页面能看到），不做邮件/短信/推送到客户端。
 * ref_type + ref_id 用来告诉前端「点了跳哪里」，而不是把 URL 存进正文里——
 * 存 URL 的话以后前端路由一改，历史通知全成死链。
 */
@Data
@TableName("ud_notification")
public class Notification {

    /** 订单相关（已发货、已完成等） */
    public static final int TYPE_ORDER = 1;
    /** 售后相关（退款到账、申请被拒） */
    public static final int TYPE_AFTER_SALE = 2;
    /** 关注的商品降价 */
    public static final int TYPE_PRICE_DROP = 3;
    /** 关注的店铺上新 */
    public static final int TYPE_SHOP_NEW = 4;

    /** 关联对象：订单 */
    public static final int REF_ORDER = 1;
    /** 关联对象：商品 */
    public static final int REF_PRODUCT = 2;
    /** 关联对象：店铺 */
    public static final int REF_SHOP = 3;

    private Long id;
    private Long userId;
    private Integer type;
    private String title;
    private String content;

    @TableField("ref_type")
    private Integer refType;

    @TableField("ref_id")
    private Long refId;

    @TableField("is_read")
    private Integer isRead;

    /**
     * 去重键。
     * 一次性事件（发货、退款）留空，永远只发一条；
     * 可重复事件（降价）填日期，同一对象同一天只发一条，避免商户频繁改价刷屏。
     * 配合唯一索引 (user_id, type, ref_type, ref_id, dedup_key) 生效。
     */
    @TableField("dedup_key")
    private String dedupKey;

    @TableField("created_at")
    private LocalDateTime createTime;
}
