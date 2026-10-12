package com.ecommerce.api.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreateOrderDTO {
    @NotEmpty(message = "请选择购物车商品")
    private List<Long> cartItemIds;
    @NotNull(message = "请选择收货地址")
    private Long addressId;
    private String remark;
}
