package com.ecommerce.feed.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广场动态
 */
@Data
@TableName("feed_post")
public class FeedPost {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 发布用户ID */
    private Long userId;
    /** 发布用户名（冗余存储，避免列表时联表查询） */
    private String username;
    /** 动态内容 */
    private String content;
    @TableField("created_at")
    private LocalDateTime createTime;
}
