package com.ecommerce.agent.agent;

import com.ecommerce.agent.llm.LlmClient;
import com.ecommerce.agent.tool.AgentTool;
import com.ecommerce.agent.tool.ProductCard;
import com.ecommerce.agent.tool.ProductRecommendationTool;
import com.ecommerce.agent.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 电商购物智能体
 * <p>
 * 有 Key（prod）时：通过 Function Calling 调用工具查询真实数据。
 * 工具子集由 Supervisor 按意图传入（分层路由），LLM 只从当前意图的工具中选，保证准确率。
 * 无 Key（dev/未配置）时：降级为关键词 + 固定话术。
 * </p>
 */
@Slf4j
@Component
@Order(1)
public class CommerceAgent implements Agent {

    private static final String LEGACY_SYSTEM_PROMPT =
            "你是电商平台的购物助手 CommerceAgent，可以调用工具查询真实业务数据。\n" +
            "回复规则：只根据工具结果和已知信息回答，不要编造。用简单、自然、口语化的中文，像店员跟顾客说话；先说结论，通常1到3句，避免术语、长段解释和过多列表。" +
            "没有查到就直接说没查到；工具失败就简短说明稍后再试。需要补充信息时，一次只问一个简短问题。不需要工具的问题直接回答。";

    private final LlmClient llmClient;
    private final ProductRecommendationTool productRecommendationTool;

    private static final String SYSTEM_PROMPT_V2 =
            "你是电商平台购物助手 CommerceAgent。"
                    + "你可以调用平台提供的真实业务工具："
                    + "recommend_products 用于商品推荐，"
                    + "product_search 用于关键词搜索商品，"
                    + "order_list 用于查询当前用户订单，"
                    + "cart_list 用于查询当前用户购物车。"
                    + "只根据工具结果回答，不要编造商品、价格、库存、订单或物流。"
                    + "需要商品时优先调用商品工具；工具返回商品卡片时不要编造图片或链接。"
                    + "涉及添加购物车、删除、退款、投诉或人工客服时，只做引导并等待用户确认，"
                    + "不要自行执行高风险操作。回答简短、自然、中文口语化。";

    public CommerceAgent(LlmClient llmClient, ProductRecommendationTool productRecommendationTool) {
        this.llmClient = llmClient;
        this.productRecommendationTool = productRecommendationTool;
    }

    @Override
    public String getName() {
        return "CommerceAgent";
    }

    @Override
    public String respond(String message, Long userId, List<Map<String, String>> history, List<AgentTool> tools) {
        return respondWithCards(message, userId, history, tools).content();
    }

    @Override
    public AgentResponse respondWithCards(String message, Long userId,
                                          List<Map<String, String>> history,
                                          List<AgentTool> tools) {
        List<ProductCard> products = new ArrayList<>();
        // 真实 LLM + 工具调用（prod + Key），历史注入实现多轮记忆
        if (llmClient.isAvailable()) {
            try {
                String reply = llmClient.chatWithTools(
                        SYSTEM_PROMPT_V2, history, message, tools, userId, products);
                return new AgentResponse(reply, products);
            } catch (Exception e) {
                log.warn("CommerceAgent LLM 调用失败，降级话术: {}", e.getMessage());
            }
        }
        // 降级：关键词 + 固定话术
        if (isProductRequest(message)) {
            ToolExecutionResult result = productRecommendationTool.executeForDisplay(
                    Map.of("keyword", extractKeyword(message)), userId);
            products.addAll(result.products());
            return new AgentResponse(
                    products.isEmpty()
                            ? "暂时没有找到合适的在售商品，可以换个关键词试试。"
                            : "我先挑了几件在售商品，你可以点开卡片查看详情。",
                    products);
        }
        return new AgentResponse(fallback(message), List.of());
    }

    private boolean isProductRequest(String message) {
        String msg = message.toLowerCase();
        return msg.contains("推荐") || msg.contains("商品") || msg.contains("产品")
                || msg.contains("买") || msg.contains("recommend")
                || msg.contains("product");
    }

    private String extractKeyword(String message) {
        return message
                .replaceAll("(?i)推荐|一下|一些|商品|产品|想要|想买|买|recommend(?:ation)?s?|products?", " ")
                .trim();
    }

    /** 无 Key 时的固定话术（保持原行为） */
    private String fallback(String message) {
        String msg = message.trim();
        if (msg.contains("商品") || msg.contains("product"))
            return "我们有很多优质商品！你可以在主页浏览，也可以用搜索框找特定商品。";
        if (msg.contains("订单") || msg.contains("order"))
            return "你可以点击右上角头像 → 我的订单查看订单状态。";
        if (msg.contains("价格") || msg.contains("price"))
            return "价格由商家自主定价。点击商品卡片可以看到详细价格信息。";
        if (msg.contains("购物车") || msg.contains("cart"))
            return "点击顶部的购物车图标可以查看你的购物车。";
        if (msg.contains("推荐"))
            return "我推荐你去主页看看“推荐”栏，那里有精选商品。";
        return "这个我可以帮你查一下！你可以试试搜索框，或者在主页浏览商品。";
    }

    @Override
    public boolean canHandle(String message) {
        String msg = message.trim().toLowerCase();
        return msg.contains("buy") || msg.contains("order") || msg.contains("product")
            || msg.contains("price") || msg.contains("cart")
            || msg.contains("商品") || msg.contains("订单")
            || msg.contains("价格") || msg.contains("购物车")
            || msg.contains("推荐");
    }
}
