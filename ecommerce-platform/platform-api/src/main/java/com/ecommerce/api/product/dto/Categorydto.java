package com.ecommerce.api.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Categorydto {
    @NotBlank(message = "品类名称不能为空")
    private String name;

    private Long parentId;

    private Integer sortOrder;
}
