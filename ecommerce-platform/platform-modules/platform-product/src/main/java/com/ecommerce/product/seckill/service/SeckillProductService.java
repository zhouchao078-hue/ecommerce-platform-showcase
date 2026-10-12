package com.ecommerce.product.seckill.service;

import com.ecommerce.product.seckill.model.SeckillProductCreateRequest;
import com.ecommerce.product.seckill.model.SeckillProductVO;
import com.ecommerce.product.seckill.model.SeckillPurchaseVO;

import java.util.List;

public interface SeckillProductService {
    List<SeckillProductVO> listAvailable();

    SeckillProductVO getAvailable(Long id);

    void create(Long merchantId, SeckillProductCreateRequest request);

    SeckillPurchaseVO purchase(Long seckillProductId, Long userId);
}
