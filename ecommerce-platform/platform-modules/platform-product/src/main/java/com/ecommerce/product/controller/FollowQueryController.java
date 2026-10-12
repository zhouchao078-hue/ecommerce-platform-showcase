package com.ecommerce.product.controller;

import com.ecommerce.api.merchant.vo.ShopVO;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.merchant.mapper.MdMerchantMapper;
import com.ecommerce.merchant.model.MdMerchant;
import com.ecommerce.merchant.model.UdFollow;
import com.ecommerce.merchant.service.FollowService;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 「我的关注」查询。
 * 写接口（关注/取关）在 platform-merchant 的 FollowController，
 * 这里只做列表查询，因为要同时读商家和商品两张表。
 */
@Slf4j
@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowQueryController {

    private final FollowService followService;
    private final MdMerchantMapper merchantMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final AuthService authService;

    /** 我关注的店铺 */
    @GetMapping("/merchants")
    public Result<List<ShopVO>> myMerchants() {
        Long userId = authService.getCurrentUserId();
        List<Long> ids = followService.listTargetIds(userId, UdFollow.TYPE_MERCHANT);
        if (ids.isEmpty()) {
            return Result.success(List.of());
        }

        List<ShopVO> result = new ArrayList<>();
        for (MdMerchant merchant : merchantMapper.selectBatchIds(ids)) {
            ShopVO vo = new ShopVO();
            vo.setId(merchant.getId());
            vo.setShopName(merchant.getShopName());
            vo.setDescription(merchant.getDescription());
            vo.setProductCount(productService.countShopProducts(merchant.getId()));
            vo.setFollowerCount(followService.countFollowers(UdFollow.TYPE_MERCHANT, merchant.getId()));
            vo.setFollowing(true);
            result.add(vo);
        }
        return Result.success(result);
    }

    /** 我关注的商品（只返回仍在架上的） */
    @GetMapping("/products")
    public Result<List<PdProduct>> myProducts() {
        Long userId = authService.getCurrentUserId();
        List<Long> ids = followService.listTargetIds(userId, UdFollow.TYPE_PRODUCT);
        if (ids.isEmpty()) {
            return Result.success(List.of());
        }
        List<PdProduct> products = productMapper.selectBatchIds(ids).stream()
                // 已下架的商品不再展示（关注记录保留，重新上架后会再出现）
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .toList();
        return Result.success(products);
    }
}
