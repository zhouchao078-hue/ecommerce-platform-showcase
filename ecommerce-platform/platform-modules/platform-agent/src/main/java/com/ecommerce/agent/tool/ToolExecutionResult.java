package com.ecommerce.agent.tool;

import java.util.List;

public record ToolExecutionResult(String content, List<ProductCard> products) {

    public ToolExecutionResult {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
