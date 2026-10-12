package com.ecommerce.agent.agent;

/**
 * 用户意图（分层路由的第一级）
 * <p>从日常用语中提取意图后，只暴露该意图对应的工具子集，保证工具选择的准确率。</p>
 */
public enum AgentIntent {

    /** 订单相关：查订单、查物流、取消等 */
    ORDER("order"),
    /** 找/买/了解商品 */
    PRODUCT("product"),
    /** 购物车相关 */
    CART("cart"),
    SUPPORT("support"),
    /** 闲聊/问候/其他非购物话题 */
    CHAT("chat"),
    /** 无法确定（兜底：走关键词路由或引导） */
    UNKNOWN("unknown");

    private final String code;

    AgentIntent(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AgentIntent fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        for (AgentIntent intent : values()) {
            if (intent.code.equalsIgnoreCase(code.trim())) {
                return intent;
            }
        }
        return UNKNOWN;
    }
}
