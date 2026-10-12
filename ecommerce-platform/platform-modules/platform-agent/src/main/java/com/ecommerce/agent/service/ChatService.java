package com.ecommerce.agent.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ChatService {
    /**
     * 处理用户聊天消息
     *
     * @param message   用户消息
     * @param sessionId 会话ID（按登录用户隔离）
     * @return 包含 reply（回复内容）和 agent（回复的智能体名称）的 Map
     */
    Map<String, Object> chat(String message, String sessionId);

    /**
     * Asynchronous variant used by the web layer to release request threads.
     */
    CompletableFuture<Map<String, Object>> chatAsync(String message, String sessionId);
}
