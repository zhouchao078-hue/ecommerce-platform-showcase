package com.ecommerce.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户画像（长期记忆项）
 */
@Data
@TableName("user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 画像项 key（如 name/preference/hobby） */
    private String profileKey;
    /** 画像值 */
    private String profileValue;
    @TableField("updated_at")
    private LocalDateTime updateTime;
}
