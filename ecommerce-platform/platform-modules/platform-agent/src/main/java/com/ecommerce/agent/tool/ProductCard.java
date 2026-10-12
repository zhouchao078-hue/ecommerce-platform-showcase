package com.ecommerce.agent.tool;

import java.math.BigDecimal;

public record ProductCard(
        Long id,
        String name,
        BigDecimal price,
        Integer stock,
        String image,
        Long merchantId,
        Long categoryId,
        String description
) {
}
