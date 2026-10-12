package com.ecommerce.api.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplyMerchantDTO {
    @NotBlank(message = "店铺名称不能为空")
    private String shopName;

    private String description;
}
