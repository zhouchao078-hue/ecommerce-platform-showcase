package com.ecommerce.product.controller;

import com.ecommerce.api.merchant.vo.ShopVO;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.common.result.Result;
import com.ecommerce.merchant.mapper.MdMerchantMapper;
import com.ecommerce.merchant.model.MdMerchant;
import com.ecommerce.merchant.model.UdFollow;
import com.ecommerce.merchant.service.FollowService;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家主页（公开页面，不需要登录）。
 * 未登录时 following 返回 false，前端据此决定关注按钮的初始状态。
 */
@Slf4j
@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopController {

    private final MdMerchantMapper merchantMapper;
    private final FollowService followService;
    private final ProductService productService;
    private final AuthService authService;

    /** 店铺信息 + 关注状态 */
    @GetMapping("/{id}")
    public Result<ShopVO> getShop(@PathVariable Long id) {
        MdMerchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        ShopVO vo = new ShopVO();
        vo.setId(merchant.getId());
        vo.setShopName(merchant.getShopName());
        vo.setDescription(merchant.getDescription());
        vo.setProductCount(productService.countShopProducts(id));
        vo.setFollowerCount(followService.countFollowers(UdFollow.TYPE_MERCHANT, id));
        vo.setFollowing(followService.isFollowing(
                authService.getCurrentUserIdOrNull(), UdFollow.TYPE_MERCHANT, id));
        return Result.success(vo);
    }

    /** 店铺在售商品列表 */
    @GetMapping("/{id}/products")
    public Result<List<PdProduct>> listShopProducts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "12") Integer pageSize) {
        return Result.success(productService.listShopProducts(id, pageNum, pageSize));
    }
}
