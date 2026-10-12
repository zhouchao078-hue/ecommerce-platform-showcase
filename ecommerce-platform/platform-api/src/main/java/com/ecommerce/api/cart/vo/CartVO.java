package com.ecommerce.api.cart.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CartVO {
    private Long id;
    private Long userId;
    private Long productId;
    private Long skuId;
    private String skuName;
    private Integer quantity;
    private String productName;
    private BigDecimal productPrice;
    private String productImage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
