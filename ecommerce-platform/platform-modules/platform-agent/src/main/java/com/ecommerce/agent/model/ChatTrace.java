package com.ecommerce.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体思考轨迹（一次对话的完整决策记录，供前端可视化）
 */
@Data
@TableName("chat_trace")
public class ChatTrace {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 会话ID（user:{userId}） */
    private String sessionId;
    /** 用户ID */
    private Long userId;
    /** 用户消息 */
    private String message;
    /** 意图分类结果（order/product/cart/chat/unknown） */
    private String intent;
    /** 路由到的智能体 */
    private String routeAgent;
    /** 工具调用步骤 JSON（每轮 tool name/args/result/耗时） */
    private String steps;
    /** 智能体最终回复 */
    private String reply;
    /** 总耗时（毫秒） */
    private Long durationMs;
    @TableField("created_at")
    private LocalDateTime createTime;
}
