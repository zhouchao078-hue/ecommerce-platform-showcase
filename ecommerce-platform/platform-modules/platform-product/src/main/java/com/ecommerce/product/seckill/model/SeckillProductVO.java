package com.ecommerce.product.seckill.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillProductVO {
    private Long id;
    private Long productId;
    private String name;
    private String description;
    private String images;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer stock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
