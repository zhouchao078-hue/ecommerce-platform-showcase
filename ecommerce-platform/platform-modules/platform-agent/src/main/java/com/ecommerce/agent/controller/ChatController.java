package com.ecommerce.agent.controller;

import com.ecommerce.agent.config.AgentExecutionProperties;
import com.ecommerce.agent.model.ChatTrace;
import com.ecommerce.agent.service.ChatService;
import com.ecommerce.agent.service.ChatTracer;
import com.ecommerce.agent.service.PetChatService;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;
    private final ChatTracer chatTracer;
    private final PetChatService petChatService;
    private final AgentExecutionProperties executionProperties;

    @PostMapping
    public DeferredResult<Result<Map<String, Object>>> chat(
            @RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        Long userId = authService.getCurrentUserId();
        return toDeferredResult(chatService.chatAsync(message, "user:" + userId));
    }

    @PostMapping("/pet")
    public DeferredResult<Result<Map<String, String>>> petChat(
            @RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        Long userId = authService.getCurrentUserId();
        return toDeferredResult(petChatService.chatAsync(message, userId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/trace")
    public Result<List<ChatTrace>> traces(
            @RequestParam(defaultValue = "20") Integer limit) {
        Long userId = authService.getCurrentUserId();
        List<ChatTrace> traces = chatTracer.listBySession("user:" + userId, limit);
        return Result.success(traces);
    }

    private <T> DeferredResult<Result<T>> toDeferredResult(CompletableFuture<T> future) {
        long timeoutMillis = Math.max(1, executionProperties.getChatTimeoutSeconds() + 5L) * 1000L;
        DeferredResult<Result<T>> deferred = new DeferredResult<>(timeoutMillis);
        future.whenComplete((data, error) -> {
            if (error == null) {
                deferred.setResult(Result.success(data));
            } else {
                deferred.setErrorResult(unwrap(error));
            }
        });
        deferred.onTimeout(() -> future.cancel(true));
        return deferred;
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
