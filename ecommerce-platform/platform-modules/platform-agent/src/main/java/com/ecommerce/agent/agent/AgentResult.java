package com.ecommerce.agent.agent;

import com.ecommerce.agent.tool.ProductCard;

import java.util.List;

public record AgentResult(String reply, String agentName, List<ProductCard> products) {

    public AgentResult {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
