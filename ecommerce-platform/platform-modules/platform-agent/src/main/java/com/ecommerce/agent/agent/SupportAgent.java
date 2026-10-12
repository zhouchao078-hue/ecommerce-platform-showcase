package com.ecommerce.agent.agent;

import com.ecommerce.agent.llm.LlmClient;
import com.ecommerce.agent.tool.AgentTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class SupportAgent implements Agent {

    private static final String SYSTEM_PROMPT =
            "你是电商平台的客服转接助手。"
                    + "你不能承诺退款、赔偿、补发或任何尚未确认的处理结果。"
                    + "当用户需要售后、投诉或人工介入时，先复述关键问题，"
                    + "再引导用户提交工单并补充订单号、商品和具体诉求。"
                    + "回答要简短、克制，不要编造处理时限。";

    private final LlmClient llmClient;

    @Override
    public String getName() {
        return "SupportAgent";
    }

    @Override
    public String respond(String message, Long userId,
                          List<Map<String, String>> history,
                          List<AgentTool> tools) {
        if (llmClient.isAvailable()) {
            try {
                return llmClient.chat(SYSTEM_PROMPT, history, message);
            } catch (Exception ex) {
                log.warn("SupportAgent LLM call failed: {}", ex.getMessage());
            }
        }
        return "这类问题需要人工客服跟进。请在页面顶部点击“客服”提交工单，"
                + "并补充订单号、商品和具体诉求。";
    }

    @Override
    public boolean canHandle(String message) {
        String text = message.toLowerCase();
        return text.contains("退款") || text.contains("售后") || text.contains("投诉")
                || text.contains("人工") || text.contains("工单") || text.contains("客服")
                || text.contains("赔偿") || text.contains("refund")
                || text.contains("complaint") || text.contains("support");
    }
}
