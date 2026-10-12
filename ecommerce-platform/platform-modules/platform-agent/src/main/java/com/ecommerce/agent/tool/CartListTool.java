package com.ecommerce.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.agent.tool.AgentTool;
import com.ecommerce.cart.mapper.CartMapper;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.model.PdProduct;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具：查询当前用户的购物车
 */
@Component
@RequiredArgsConstructor
public class CartListTool implements AgentTool {

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "cart_list";
    }

    @Override
    public String getCategory() {
        return "cart";
    }

    @Override
    public String getDescription() {
        return "用户询问购物车时调用（购物车里有啥、买了什么），返回当前用户购物车商品（商品名、单价、数量）";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public String execute(Map<String, Object> args, Long userId) {
        if (userId == null) {
            return "用户未登录";
        }
        QueryWrapper<Cart> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        List<Cart> carts = cartMapper.selectList(wrapper);
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Cart cart : carts) {
                PdProduct product = productMapper.selectById(cart.getProductId());
                result.add(Map.of(
                        "productName", product != null ? product.getName() : "未知商品",
                        "price", product != null ? product.getPrice() : 0,
                        "quantity", cart.getQuantity()));
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "[]";
        }
    }
}
