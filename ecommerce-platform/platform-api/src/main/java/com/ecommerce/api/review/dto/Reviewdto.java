package com.ecommerce.api.review.dto;

import lombok.Data;

@Data
public class Reviewdto {
    Long productId;
    Long orderId;
    Integer rating;
    String content;
}
