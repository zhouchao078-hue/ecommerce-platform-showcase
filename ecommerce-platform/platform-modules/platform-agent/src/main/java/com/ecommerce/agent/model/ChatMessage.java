package com.ecommerce.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体聊天消息（会话历史持久化）
 */
@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话ID（user:{userId}） */
    private String sessionId;
    /** 用户ID */
    private Long userId;
    /** 角色：user / assistant / 智能体名称 */
    private String role;
    /** 消息内容 */
    private String content;
    @TableField("created_at")
    private LocalDateTime createTime;
}
