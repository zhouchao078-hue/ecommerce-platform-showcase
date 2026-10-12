package com.ecommerce.agent.agent;

import com.ecommerce.agent.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 桌宠智能体（闲聊陪伴）
 * <p>
 * 有 Key（prod）：LLM 驱动的陪伴聊天，有宠物人格设定；
 * 无 Key（dev/未配置）：降级为关键词 + 固定话术。
 * </p>
 */
@Slf4j
@Component
@Order(2)
public class PetAgent implements Agent {

    private static final String SYSTEM_PROMPT =
            "你是电商平台的桌宠智能体 PetAgent，一只住在用户屏幕角落里的小宠物。\n" +
            "性格：活泼可爱、元气满满、亲近用户，说话带语气词（～、哦、呀、啦）。\n" +
            "回复要求：像朋友聊天一样自然、简单、口语化；先回应用户最关心的事，通常一到两句话，尽量不超过50字。" +
            "少用专业词、长列表和夸张语气词，不要重复问题。遇到购物、订单等业务问题，告诉用户可以打开购物助手查询；不要假装查过数据。";

    private final LlmClient llmClient;

    public PetAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getName() {
        return "PetAgent";
    }

    @Override
    public String respond(String message, Long userId, List<Map<String, String>> history,
                          List<com.ecommerce.agent.tool.AgentTool> tools) {
        // 真实 LLM 闲聊（prod + Key），历史注入让桌宠记得聊过什么
        if (llmClient.isOllamaAvailable()) {
            try {
                return llmClient.chatWithOllama(SYSTEM_PROMPT, history, message);
            } catch (Exception e) {
                log.warn("PetAgent LLM 调用失败，降级话术: {}", e.getMessage());
            }
        }
        // 降级：关键词 + 固定话术
        return fallback(message);
    }

    /** 无 Key 时的固定话术（保持原行为） */
    private String fallback(String message) {
        String msg = message.trim();
        if (msg.contains("你好") || msg.contains("喂") || msg.contains("hi") || msg.contains("hello"))
            return "你好呀！我是你的电商小助手～ 有什么可以帮你的？";
        if (msg.contains("谢谢") || msg.contains("thanks"))
            return "不客气！随时找我聊天哦～";
        if (msg.contains("天") || msg.contains("weather"))
            return "今天天气不错，适合网购！看看有没有喜欢的商品吧～";
        return "嗯嗯，我在听呢～ 你可以问我商品推荐、订单查询、价格对比这些问题哦！";
    }

    @Override
    public boolean canHandle(String message) {
        return false;
    }
}
