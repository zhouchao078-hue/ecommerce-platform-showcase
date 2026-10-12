package com.ecommerce.agent.agent;

import com.ecommerce.agent.tool.ProductCard;

import java.util.List;

public record AgentResponse(String content, List<ProductCard> products) {

    public AgentResponse {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
