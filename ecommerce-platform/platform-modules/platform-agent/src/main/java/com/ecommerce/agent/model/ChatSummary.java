package com.ecommerce.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话摘要（长对话滚动压缩的缓存）
 */
@Data
@TableName("chat_summary")
public class ChatSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话ID（user:{userId}） */
    private String sessionId;
    /** 摘要内容 */
    private String summary;
    @TableField("updated_at")
    private LocalDateTime updateTime;
}
