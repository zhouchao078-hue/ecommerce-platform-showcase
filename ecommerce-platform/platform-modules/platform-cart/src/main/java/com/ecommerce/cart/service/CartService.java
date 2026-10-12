package com.ecommerce.cart.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.api.cart.vo.CartVO;

public interface CartService {
    IPage<CartVO> listCart(Long userId, Integer pageNum, Integer pageSize);

    void addCart(Long userId, Long productId, Long skuId, Integer quantity);

    void updateCartQuantity(Long id, Long userId, Integer quantity);
    void deleteCart(Long id, Long userId);
}
