package com.ecommerce.agent.agent;

import com.ecommerce.agent.tool.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * 智能体接口
 * <p>
 * 所有智能体实现此接口。respond 接收：
 * - userId：当前登录用户（工具查询用）
 * - history：历史消息 {role, content}（role ∈ user/assistant/memory，memory 注入 system）
 * - tools：当前意图对应的工具子集（分层路由传入，保证工具选择准确率）
 * </p>
 */
public interface Agent {
    /** 智能体名称 */
    String getName();

    /**
     * 回复用户消息
     *
     * @param message 用户消息
     * @param userId  当前登录用户ID（可为 null）
     * @param history 上下文（含 memory 段与最近对话，可为 null）
     * @param tools   本次可用的工具子集（按意图过滤后）
     * @return 回复内容
     */
    String respond(String message, Long userId, List<Map<String, String>> history, List<AgentTool> tools);

    default AgentResponse respondWithCards(String message, Long userId,
                                           List<Map<String, String>> history,
                                           List<AgentTool> tools) {
        return new AgentResponse(respond(message, userId, history, tools), List.of());
    }

    /**
     * 关键词路由降级用：判断是否能处理该消息
     */
    boolean canHandle(String message);
}
