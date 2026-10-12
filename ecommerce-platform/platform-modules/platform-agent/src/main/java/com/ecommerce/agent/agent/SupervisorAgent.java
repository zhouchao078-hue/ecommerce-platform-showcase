package com.ecommerce.agent.agent;

import com.ecommerce.agent.llm.LlmClient;
import com.ecommerce.agent.service.AgentSessionStore;
import com.ecommerce.agent.service.ChatTracer;
import com.ecommerce.agent.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能体调度器（Supervisor）
 * <p>
 * 职责：分层路由 —— ① LLM 意图分类（few-shot，order/product/cart/chat）
 * → ② 按意图挑 Agent + 只暴露该意图的工具子集 → ③ Agent 回复 → 记录历史。
 * 工具子集机制保证：工具总量增长时，LLM 每次仍只从 1~3 个工具里选，准确率不下降。
 * 无 Key：关键词意图降级。
 * </p>
 */
@Slf4j
@Component
public class SupervisorAgent {

    private static final String LEGACY_CLASSIFY_PROMPT =
            "你是电商智能体的意图识别器。判断用户消息的意图，只输出 JSON：{\"intent\":\"order\"}。\n" +
            "意图定义与典型说法：\n" +
            "- order（订单）：'我的订单到哪了' '查一下订单' '那单发货没' '我要退款' '昨天买的啥时候到' '订单怎么还没到'\n" +
            "- product（找/买商品）：'有卖茶叶吗' '推荐个手机' '保温杯多少钱' '这个牌子有什么型号' '想买台相机'\n" +
            "- cart（购物车）：'购物车里有啥' '把这件加购物车' '购物车里的东西多少钱'\n" +
            "- chat（闲聊/其他）：'你好' '好累' '讲个笑话' '今天天气怎么样' '谢谢你'\n" +
            "无法确定时输出 {\"intent\":\"unknown\"}。只输出 JSON，不要解释。";

    private static final String CLASSIFY_PROMPT_V2 =
            "你是电商平台的意图路由器，负责判断用户下一步需要哪类能力。\n"
                    + "只输出 JSON，不要解释，不要输出 Markdown。\n"
                    + "可选 intent：\n"
                    + "- product：搜索商品、比较商品、推荐商品、询问价格库存或商品参数\n"
                    + "- order：查询订单、物流、支付状态、订单详情\n"
                    + "- cart：查看购物车，或用户明确要求修改购物车\n"
                    + "- support：退款、售后、投诉、人工客服、创建或查询工单、高风险问题\n"
                    + "- chat：问候、闲聊、情绪陪伴、与购物无关的问题\n"
                    + "- unknown：信息不足，无法判断\n"
                    + "action 只能是 read、write、unknown 之一。\n"
                    + "规则：\n"
                    + "1. 退款、投诉、人工、售后、工单优先归为 support。\n"
                    + "2. 用户要求加入购物车、删除商品、取消订单属于 write。\n"
                    + "3. 查询类操作属于 read。\n"
                    + "4. 不确定时使用 unknown，不要猜测。\n"
                    + "输出格式：{\"intent\":\"product\",\"confidence\":0.0,"
                    + "\"action\":\"read\",\"entities\":{\"keyword\":null,"
                    + "\"maxPrice\":null,\"orderId\":null,\"ticketId\":null}}";

    private static final Pattern INTENT_PATTERN = Pattern.compile("\"intent\"\\s*:\\s*\"(\\w+)\"");

    /** 会话历史容量上限（防内存膨胀） */
    /** 意图分类时注入的最近对话轮数（条数 = 轮数 × 2） */
    private static final int INTENT_CONTEXT_MESSAGES = 6;

    private final List<Agent> agents;
    private final LlmClient llmClient;
    private final List<AgentTool> tools;
    private final AgentSessionStore sessionStore;

    public SupervisorAgent(List<Agent> agents, LlmClient llmClient, List<AgentTool> tools,
                           AgentSessionStore sessionStore) {
        this.agents = agents.stream()
                .sorted(Comparator.comparing(a -> {
                    Order order = a.getClass().getAnnotation(Order.class);
                    return order != null ? order.value() : Ordered.LOWEST_PRECEDENCE;
                }))
                .toList();
        this.llmClient = llmClient;
        this.tools = tools;
        this.sessionStore = sessionStore;
    }

    /**
     * 处理消息：意图分类 → 路由（Agent + 工具子集）→ 记录历史
     */
    public AgentResult process(String message, String sessionId, Long userId, List<Map<String, String>> history) {
        addToHistory(sessionId, "user", message);

        // ① 意图分类（LLM few-shot / 关键词降级）
        AgentIntent intent = classifyIntent(message, history);

        // ② 选 Agent + 过滤工具子集
        Agent agent = pickAgent(intent, message);
        List<AgentTool> toolsForIntent = filterTools(intent);

        // ③ 记录思考轨迹（意图 + 路由），供前端可视化
        ChatTracer.recordIntent(intent.getCode());
        ChatTracer.recordRoute(agent.getName());

        // ④ 让 Agent 回复（只暴露当前意图的工具）
        AgentResponse response = agent.respondWithCards(message, userId, history, toolsForIntent);
        String reply = response.content();

        addToHistory(sessionId, agent.getName(), reply);
        return new AgentResult(reply, agent.getName(), response.products());
    }

    /** 意图分类：LLM few-shot → 失败/unknown 降级关键词 */
    private AgentIntent classifyIntent(String message, List<Map<String, String>> history) {
        if (isSupportMessage(message)) {
            return AgentIntent.SUPPORT;
        }
        if (llmClient.isAvailable()) {
            try {
                List<Map<String, String>> recentContext = recentContext(history);
                String json = llmClient.chat(CLASSIFY_PROMPT_V2, recentContext, message);
                AgentIntent intent = AgentIntent.fromCode(parseIntent(json));
                if (intent != AgentIntent.UNKNOWN) {
                    return intent;
                }
                log.debug("LLM 意图分类 unknown: 原文={}", json);
            } catch (Exception e) {
                log.warn("LLM 意图分类失败，降级关键词: {}", e.getMessage());
            }
        }
        return keywordIntent(message);
    }

    private boolean isSupportMessage(String message) {
        String text = message == null ? "" : message.trim().toLowerCase();
        return text.contains("退款") || text.contains("售后") || text.contains("投诉")
                || text.contains("人工") || text.contains("工单") || text.contains("客服")
                || text.contains("赔偿") || text.contains("refund")
                || text.contains("complaint") || text.contains("support");
    }

    /** 关键词意图降级 */
    private AgentIntent keywordIntent(String message) {
        String msg = message.trim().toLowerCase();
        if (msg.contains("购物车") || msg.contains("cart")) return AgentIntent.CART;
        if (msg.contains("订单") || msg.contains("order") || msg.contains("退款") || msg.contains("发货")) return AgentIntent.ORDER;
        if (msg.contains("商品") || msg.contains("产品") || msg.contains("推荐") || msg.contains("价格")
                || msg.contains("买") || msg.contains("product") || msg.contains("price")) return AgentIntent.PRODUCT;
        return AgentIntent.CHAT;
    }

    /** 按意图挑 Agent */
    private Agent pickAgent(AgentIntent intent, String message) {
        switch (intent) {
            case ORDER:
            case PRODUCT:
            case CART: {
                Agent agent = findByName("CommerceAgent");
                if (agent != null) return agent;
                break;
            }
            case SUPPORT: {
                Agent agent = findByName("SupportAgent");
                if (agent != null) return agent;
                break;
            }
            case CHAT: break; // PetAgent 由独立桌宠接口调用，不参与业务意图路由
            default: break; // UNKNOWN → 关键词 canHandle 兜底
        }
        // UNKNOWN / 找不到对应 Agent：关键词路由兜底
        for (Agent agent : agents) {
            if (agent.canHandle(message)) {
                return agent;
            }
        }
        return agents.isEmpty() ? new FallbackAgent() : agents.get(0);
    }

    /** 按意图过滤工具子集（分层路由核心：只暴露当前意图的工具） */
    private List<AgentTool> filterTools(AgentIntent intent) {
        String category = intent.getCode(); // product/order/cart 与工具 category 对齐
        return tools.stream()
                .filter(t -> category.equals(t.getCategory()))
                .toList();
    }

    /** 取最近 N 条非 memory 历史用于意图分类 */
    private List<Map<String, String>> recentContext(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> filtered = history.stream()
                .filter(h -> !"memory".equals(h.get("role")))
                .toList();
        if (filtered.size() <= INTENT_CONTEXT_MESSAGES) {
            return filtered;
        }
        return filtered.subList(filtered.size() - INTENT_CONTEXT_MESSAGES, filtered.size());
    }

    /** 从 LLM 返回的 JSON 中提取 intent 字段 */
    private String parseIntent(String json) {
        if (json == null) return "";
        Matcher matcher = INTENT_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private Agent findByName(String name) {
        for (Agent agent : agents) {
            if (agent.getName().equals(name)) {
                return agent;
            }
        }
        return null;
    }

    /** 会话历史管理：每会话有容量上限，超出丢弃最旧记录 */
    public void addToHistory(String sessionId, String role, String content) {
        sessionStore.append(sessionId, role, content);
    }

    public List<String> getHistory(String sessionId) {
        return sessionStore.get(sessionId);
    }

    /** 无任何 Agent 时的兜底回复 */
    private static class FallbackAgent implements Agent {
        @Override public String getName() { return "System"; }
        @Override public String respond(String message, Long userId, List<Map<String, String>> history,
                                        List<AgentTool> tools) { return "抱歉，我暂时无法回复。"; }
        @Override public boolean canHandle(String message) { return false; }
    }
}
