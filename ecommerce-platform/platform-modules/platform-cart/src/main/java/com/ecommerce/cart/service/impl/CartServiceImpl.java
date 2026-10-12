package com.ecommerce.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.cart.mapper.CartMapper;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.api.cart.vo.CartVO;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.mapper.ProductSkuMapper;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.model.PdProductSku;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    @Override
    public IPage<CartVO> listCart(Long userId, Integer pageNum, Integer pageSize) {
        QueryWrapper<Cart> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        Page<Cart> page = new Page<>(pageNum, pageSize);
        IPage<Cart> cartPage = cartMapper.selectPage(page, wrapper);

        List<CartVO> voList = cartPage.getRecords().stream().map(cart -> {
            CartVO vo = new CartVO();
            vo.setId(cart.getId());
            vo.setUserId(cart.getUserId());
            vo.setProductId(cart.getProductId());
            vo.setSkuId(cart.getSkuId());
            vo.setQuantity(cart.getQuantity());
            vo.setCreateTime(cart.getCreateTime());
            vo.setUpdateTime(cart.getUpdateTime());
            PdProduct product = productMapper.selectById(cart.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductImage(product.getImages());
            }
            PdProductSku sku = cart.getSkuId() == null ? null : productSkuMapper.selectById(cart.getSkuId());
            if (sku != null) {
                vo.setSkuName(sku.getSkuName());
                vo.setProductPrice(sku.getPrice());
            } else if (product != null) {
                vo.setProductPrice(product.getPrice());
            }
            return vo;
        }).collect(Collectors.toList());

        Page<CartVO> voPage = new Page<>(cartPage.getCurrent(), cartPage.getSize(), cartPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public void addCart(Long userId, Long productId, Long skuId, Integer quantity) {
        PdProduct product = productMapper.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品不存在或已下架");
        }
        PdProductSku sku = resolveSku(productId, skuId);
        int qty = quantity == null || quantity <= 0 ? 1 : quantity;
        if (sku.getStock() == null || sku.getStock() < qty) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规格库存不足");
        }
        QueryWrapper<Cart> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("sku_id", sku.getId());
        Cart existing = cartMapper.selectOne(wrapper);
        if (existing != null) {
            int newQuantity = existing.getQuantity() + qty;
            if (sku.getStock() == null || sku.getStock() < newQuantity) {
                throw new BizException(ErrorCode.BAD_REQUEST, "规格库存不足");
            }
            existing.setQuantity(newQuantity);
            existing.setUpdateTime(LocalDateTime.now());
            cartMapper.updateById(existing);
        } else {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(productId);
            cart.setSkuId(sku.getId());
            cart.setQuantity(qty);
            cart.setCreateTime(LocalDateTime.now());
            cart.setUpdateTime(LocalDateTime.now());
            cartMapper.insert(cart);
        }
    }

    private PdProductSku resolveSku(Long productId, Long skuId) {
        if (skuId != null) {
            PdProductSku sku = productSkuMapper.selectById(skuId);
            if (sku == null || !productId.equals(sku.getProductId()) || !Integer.valueOf(1).equals(sku.getStatus())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "商品规格不存在或已停用");
            }
            return sku;
        }
        QueryWrapper<PdProductSku> wrapper = new QueryWrapper<>();
        wrapper.eq("product_id", productId).eq("status", 1)
                .orderByDesc("is_default").orderByAsc("id");
        List<PdProductSku> skus = productSkuMapper.selectList(wrapper);
        if (skus.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品没有可用规格");
        }
        return skus.stream()
                .filter(sku -> sku.getStock() != null && sku.getStock() > 0)
                .findFirst()
                .orElse(skus.get(0));
    }

    @Override
    public void updateCartQuantity(Long id, Long userId, Integer quantity) {
        Cart cart = cartMapper.selectById(id);
        if (cart == null) throw new BizException(ErrorCode.NOT_FOUND, "购物车记录不存在");
        if (!cart.getUserId().equals(userId)) throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此购物车记录");
        if (quantity == null || quantity <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品数量必须大于 0");
        }
        PdProductSku sku = cart.getSkuId() == null ? null : productSkuMapper.selectById(cart.getSkuId());
        if (sku != null && (sku.getStock() == null || sku.getStock() < quantity)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规格库存不足");
        }
        cart.setQuantity(quantity);
        cart.setUpdateTime(LocalDateTime.now());
        cartMapper.updateById(cart);
    }

    @Override
    public void deleteCart(Long id, Long userId) {
        Cart cart = cartMapper.selectById(id);
        if (cart == null) throw new BizException(ErrorCode.NOT_FOUND, "购物车记录不存在");
        if (!cart.getUserId().equals(userId)) throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此购物车记录");
        cartMapper.deleteById(id);
    }
}
