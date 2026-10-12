package com.ecommerce.agent.llm;

import com.ecommerce.agent.config.AgentProperties;
import com.ecommerce.agent.config.AgentExecutionProperties;
import com.ecommerce.agent.service.ChatTracer;
import com.ecommerce.agent.tool.AgentTool;
import com.ecommerce.agent.tool.ProductCard;
import com.ecommerce.agent.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实 LLM 客户端（OpenAI 兼容接口，对接 DeepSeek）
 * <p>
 * prod 环境且配置了 API Key 时启用；调用失败由上层回退关键词路由。
 * 支持 Function Calling：LLM 可请求调用工具，工具在服务端执行后结果喂回 LLM 组织回答。
 * </p>
 */
@Slf4j
@Component
public class LlmClient {

    /** 工具调用最大轮数（防止死循环） */
    private static final int MAX_TOOL_ROUNDS = 5;
    /** 工具连续失败多少次后熔断（停止重试，引导友好收尾） */
    private static final int MAX_TOOL_CONSECUTIVE_FAILURES = 2;

    private final AgentProperties props;
    private final AgentExecutionProperties executionProperties;
    private final LlmConcurrencyLimiter concurrencyLimiter;
    private final LlmCallGuard callGuard;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public LlmClient(AgentProperties props,
                     AgentExecutionProperties executionProperties,
                     LlmConcurrencyLimiter concurrencyLimiter,
                     LlmCallGuard callGuard,
                     ObjectMapper objectMapper) {
        this.props = props;
        this.executionProperties = executionProperties;
        this.concurrencyLimiter = concurrencyLimiter;
        this.callGuard = callGuard;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
        log.info("LLM endpoints initialized: supervisor model={}, keyConfigured={}, endpoint={}; "
                        + "commerce model={}, keyConfigured={}, endpoint={}; pet Ollama model={}, endpoint={}",
                props.getSupervisor().getModel(),
                hasKey(props.getSupervisor().getApiKey()),
                props.getSupervisor().getEndpoint(),
                props.getCommerce().getModel(),
                hasKey(props.getCommerce().getApiKey()),
                props.getCommerce().getEndpoint(),
                props.getLocal().getModel(),
                props.getLocal().getEndpoint());
    }

    /**
     * 是否启用真实 LLM：prod 环境 + 已配置有效 API Key
     */
    public boolean isAvailable() {
        if (!props.isProd()) {
            return false;
        }
        return hasKey(props.getSupervisor().getApiKey());
    }

    private boolean hasKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank() && !apiKey.contains("your-");
    }

    /**
     * 普通对话（无工具）
     */
    public String chat(String systemPrompt, List<Map<String, String>> history, String userMessage) {
        List<Map<String, Object>> messages = buildMessages(systemPrompt, history, userMessage);
        Map<?, ?> response = callLlm(messages, null);
        Map<?, ?> message = firstMessage(response);
        Object content = message.get("content");
        if (content == null) {
            throw new IllegalStateException("LLM 返回无内容");
        }
        return content.toString().trim();
    }

    public boolean isOllamaAvailable() {
        return props.getLocal().getEndpoint() != null && !props.getLocal().getEndpoint().isBlank()
                && props.getLocal().getModel() != null && !props.getLocal().getModel().isBlank();
    }

    public String chatWithOllama(String systemPrompt, List<Map<String, String>> history, String userMessage) {
        List<Map<String, Object>> messages = buildMessages(systemPrompt, history, userMessage);
        Map<?, ?> response = callOllama(messages, null);
        Map<?, ?> message = firstMessage(response);
        Object content = message.get("content");
        if (content == null) {
            throw new IllegalStateException("Ollama 返回无内容");
        }
        return content.toString().trim();
    }

    /**
     * 组装 OpenAI 消息列表。
     * history 中 role=memory 的条目（画像/摘要）会拼进 system 提示词，不进入对话流。
     * 返回结构：[system(+memory), ...history明细, user(当前消息)]
     */
    private List<Map<String, Object>> buildMessages(String systemPrompt, List<Map<String, String>> history,
                                                    String userMessage) {
        StringBuilder system = new StringBuilder(systemPrompt);
        List<Map<String, Object>> messages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> h : history) {
                String role = h.getOrDefault("role", "user");
                String content = h.getOrDefault("content", "");
                if ("memory".equals(role)) {
                    // 长期记忆（画像/摘要）拼进 system，不占用对话轮次
                    system.append("\n---\n").append(content);
                } else {
                    String apiRole = "user".equals(role) ? "user" : "assistant";
                    messages.add(Map.of("role", apiRole, "content", content));
                }
            }
        }
        messages.add(0, Map.of("role", "system", "content", system.toString()));
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    /**
     * 带工具调用的对话（Function Calling 循环）
     *
     * @param systemPrompt 系统提示词
     * @param history      历史消息
     * @param userMessage  当前用户消息
     * @param tools        可用工具列表
     * @param userId       当前登录用户ID（传给工具）
     * @return LLM 最终回复
     */
    public String chatWithTools(String systemPrompt, List<Map<String, String>> history, String userMessage,
                                List<AgentTool> tools, Long userId) {
        return chatWithTools(systemPrompt, history, userMessage, tools, userId, null);
    }

    public String chatWithTools(String systemPrompt, List<Map<String, String>> history, String userMessage,
                                List<AgentTool> tools, Long userId,
                                List<ProductCard> collectedProducts) {
        if (!isAvailable()) {
            throw new IllegalStateException("LLM 未配置（需 prod 环境 + API Key）");
        }

        // 组装 messages：system(+memory) + 历史明细 + 当前用户消息
        List<Map<String, Object>> messages = buildMessages(systemPrompt, history, userMessage);

        // 工具规格（OpenAI function calling 格式）
        List<Map<String, Object>> toolSpecs = tools.stream()
                .map(t -> Map.<String, Object>of(
                        "type", "function",
                        "function", Map.of(
                                "name", t.getName(),
                                "description", t.getDescription(),
                                "parameters", t.getParameters())))
                .toList();

        // 循环：LLM 请求工具 → 执行 → 结果喂回 → 直到 LLM 直接回答
        int consecutiveFailures = 0;
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<?, ?> response = callLlm(messages, toolSpecs);
            Map<?, ?> message = firstMessage(response);
            List<?> toolCalls = (List<?>) message.get("tool_calls");

            // 没有工具调用 → LLM 直接给出了最终回答
            if (toolCalls == null || toolCalls.isEmpty()) {
                Object content = message.get("content");
                if (content == null) {
                    throw new IllegalStateException("LLM 返回无内容");
                }
                return content.toString().trim();
            }

            // 把 assistant 的 tool_calls 消息加入对话（content 可能为 null，需用 HashMap）
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", message.get("content"));
            assistantMsg.put("tool_calls", message.get("tool_calls"));
            messages.add(assistantMsg);

            // 逐个执行工具，结果作为 tool 消息追加
            boolean roundHasFailure = false;
            List<Map<String, Object>> roundCalls = new ArrayList<>();
            for (Object tc : toolCalls) {
                Map<?, ?> toolCall = (Map<?, ?>) tc;
                String callId = String.valueOf(toolCall.get("id"));
                Map<?, ?> fn = (Map<?, ?>) toolCall.get("function");
                String fnName = String.valueOf(fn.get("name"));
                Object rawArgs = fn.get("arguments");
                String fnArgs = rawArgs instanceof String ? (String) rawArgs : toJson(rawArgs);

                String result;
                AgentTool tool = tools.stream()
                        .filter(t -> t.getName().equals(fnName))
                        .findFirst()
                        .orElse(null);
                if (tool == null) {
                    result = "{\"error\":\"未知工具: " + fnName + "\"}";
                } else {
                    try {
                        Map<String, Object> args = objectMapper.readValue(fnArgs, Map.class);
                        ToolExecutionResult execution = tool.executeForDisplay(args, userId);
                        result = execution.content();
                        if (collectedProducts != null) {
                            collectedProducts.addAll(execution.products());
                        }
                    } catch (Exception e) {
                        log.warn("工具执行失败: tool={}, args={}, 原因={}", fnName, fnArgs, e.getMessage());
                        result = "{\"error\":\"工具执行失败: " + e.getMessage() + "\"}";
                    }
                }
                messages.add(Map.of("role", "tool", "tool_call_id", callId, "content", result));

                boolean failed = isToolError(result);
                roundCalls.add(Map.of(
                        "tool", fnName,
                        "args", truncate(fnArgs, 200),
                        "result", truncate(result, 400),
                        "ok", !failed));
                if (failed) {
                    roundHasFailure = true;
                }
            }
            // 思考轨迹：记录本轮工具调用
            Map<String, Object> roundStep = new HashMap<>();
            roundStep.put("round", round + 1);
            roundStep.put("calls", roundCalls);
            ChatTracer.recordToolRound(roundStep);

            // 连续失败熔断：工具反复报错时，不让 LLM 无限重试，引导其友好收尾
            consecutiveFailures = roundHasFailure ? consecutiveFailures + 1 : 0;
            if (consecutiveFailures >= MAX_TOOL_CONSECUTIVE_FAILURES) {
                messages.add(Map.of("role", "user", "content",
                        "注意：工具连续多次调用失败，请立即停止调用任何工具，直接用中文告知用户：查询服务暂时繁忙，请稍后再试。"));
                consecutiveFailures = 0; // 给 LLM 一次机会友好收尾；若仍失败由轮数上限兜底
            }
        }
        // 所有轮次耗尽仍未得到最终回答：返回友好兜底（而不是裸异常）
        return "抱歉，我暂时没能查到你要的信息，可能是服务繁忙，请稍后再试。";
    }

    /** 判断工具返回是否为错误 JSON */
    private boolean isToolError(String result) {
        if (result == null) {
            return true;
        }
        String t = result.trim();
        return t.startsWith("{\"error\"") || t.contains("\"error\":");
    }

    /** 截断长文本 */
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** 取响应中的第一条 assistant message */
    private Map<?, ?> firstMessage(Map<?, ?> response) {
        if (response == null) {
            throw new IllegalStateException("LLM 返回为空");
        }
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM 返回无 choices");
        }
        Map<?, ?> first = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) first.get("message");
        if (message == null) {
            throw new IllegalStateException("LLM 返回无 message");
        }
        return message;
    }

    /** 调用 DeepSeek chat completions 接口 */
    private Map<?, ?> callLlm(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getSupervisor().getModel());
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        body.put("stream", false);

        try (LlmConcurrencyLimiter.Permit ignored = concurrencyLimiter.acquire()) {
            return callGuard.execute("supervisor", () -> webClient.post()
                    .uri(props.getSupervisor().getEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getSupervisor().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(
                            Math.max(1, executionProperties.getLlmTimeoutSeconds())))
                    .block());
        }
    }

    private Map<?, ?> callOllama(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.getLocal().getModel());
        body.put("messages", messages);
        body.put("stream", false);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        Map<?, ?> response;
        try (LlmConcurrencyLimiter.Permit ignored = concurrencyLimiter.acquire()) {
            response = callGuard.execute("ollama", () -> webClient.post()
                    .uri(props.getLocal().getEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(
                            Math.max(1, executionProperties.getLlmTimeoutSeconds())))
                    .block());
        }
        if (response == null || !(response.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("Ollama 返回内容为空或格式不正确");
        }
        return Map.of("choices", List.of(Map.of("message", message)));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("无法解析 Ollama 工具参数", e);
        }
    }
}
