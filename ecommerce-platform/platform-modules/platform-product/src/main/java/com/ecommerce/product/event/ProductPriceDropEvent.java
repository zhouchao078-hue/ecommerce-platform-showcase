package com.ecommerce.product.event;

import java.math.BigDecimal;

/**
 * 商品降价领域事件。
 *
 * 商品模块只表达“发生了什么”，不关心谁来通知、怎么通知，
 * 避免商品模块反向依赖关注和站内通知的实现。
 */
public record ProductPriceDropEvent(
        Long productId,
        String productName,
        BigDecimal oldPrice,
        BigDecimal newPrice
) {
}
