package com.ecommerce.agent.service;

import com.ecommerce.agent.agent.PetAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Pet chat uses the same admission control and timeout boundary as other agents.
 */
@Service
@RequiredArgsConstructor
public class PetChatService {

    private final PetAgent petAgent;
    private final ChatHistoryService chatHistoryService;
    private final AgentTaskRunner taskRunner;

    public Map<String, String> chat(String message, Long userId) {
        try {
            return chatAsync(message, userId).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    public CompletableFuture<Map<String, String>> chatAsync(String message, Long userId) {
        String normalized = message == null ? "" : message.trim();
        if (normalized.isEmpty()) {
            return CompletableFuture.completedFuture(
                    Map.of("reply", "陪我说句话吧。", "agent", "PetAgent"));
        }
        return taskRunner.submit(userId, "PetAgent", () -> {
            String sessionId = "pet:" + userId;
            List<Map<String, String>> history = chatHistoryService.listBySession(sessionId, 12)
                    .stream()
                    .map(item -> Map.of(
                            "role", item.getRole(),
                            "content", item.getContent()))
                    .toList();
            String reply = petAgent.respond(normalized, userId, history, List.of());
            chatHistoryService.save(sessionId, userId, "user", normalized);
            chatHistoryService.save(sessionId, userId, "PetAgent", reply);
            return Map.of("reply", reply, "agent", "PetAgent");
        });
    }
}
