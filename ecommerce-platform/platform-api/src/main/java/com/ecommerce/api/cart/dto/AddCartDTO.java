package com.ecommerce.api.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddCartDTO {
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 不传时自动选择商品默认 SKU */
    private Long skuId;

    private Integer quantity = 1;
}
