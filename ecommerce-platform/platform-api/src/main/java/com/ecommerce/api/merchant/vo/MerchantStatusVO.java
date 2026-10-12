package com.ecommerce.api.merchant.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantStatusVO {
    private Long userId;
    private Integer status;
    private String statusDesc;
}
