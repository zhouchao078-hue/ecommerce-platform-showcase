package com.ecommerce.agent.service.impl;

import com.ecommerce.agent.agent.AgentResult;
import com.ecommerce.agent.agent.SupervisorAgent;
import com.ecommerce.agent.service.AgentTaskRunner;
import com.ecommerce.agent.service.ChatHistoryService;
import com.ecommerce.agent.service.ChatService;
import com.ecommerce.agent.service.ChatTracer;
import com.ecommerce.agent.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Chat orchestration behind a shared bounded execution boundary.
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final SupervisorAgent supervisor;
    private final ChatHistoryService chatHistoryService;
    private final MemoryService memoryService;
    private final ChatTracer chatTracer;
    private final AgentTaskRunner taskRunner;

    @Override
    public Map<String, Object> chat(String message, String sessionId) {
        try {
            return chatAsync(message, sessionId).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> chatAsync(String message, String sessionId) {
        Long userId = parseUserId(sessionId);
        return taskRunner.submit(
                userId,
                "CommerceAgent",
                () -> doChat(message, sessionId, userId));
    }

    private Map<String, Object> doChat(String message, String sessionId, Long userId) {
        ChatTracer.TraceBuilder trace = chatTracer.begin(sessionId, userId, message);
        String replyText = null;
        String agentName = null;
        try {
            List<Map<String, String>> context = memoryService.buildContext(sessionId, userId);
            AgentResult result = supervisor.process(message, sessionId, userId, context);
            replyText = result.reply();
            agentName = result.agentName();

            chatHistoryService.save(sessionId, userId, "user", message);
            chatHistoryService.save(sessionId, userId, agentName, replyText);
            memoryService.maybeSummarize(sessionId);
            memoryService.maybeExtractProfile(sessionId, userId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("reply", replyText);
            response.put("agent", agentName);
            response.put("products", result.products());
            return response;
        } finally {
            chatTracer.endAndSave(trace != null ? trace : ChatTracer.current(), replyText);
        }
    }

    private Long parseUserId(String sessionId) {
        try {
            if (sessionId != null && sessionId.startsWith("user:")) {
                return Long.valueOf(sessionId.substring("user:".length()));
            }
        } catch (NumberFormatException ignored) {
            // Anonymous sessions are allowed; Redis counters use an anonymous key.
        }
        return null;
    }
}
