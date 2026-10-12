package com.ecommerce.merchant.controller;

import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.merchant.model.UdFollow;
import com.ecommerce.merchant.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关注商家 / 关注商品。
 * 所有接口都需要登录，userId 从 JWT 取（AuthService），Controller 不碰 SecurityContextHolder。
 */
@Slf4j
@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final AuthService authService;

    @PostMapping("/merchant/{merchantId}")
    public Result<Void> followMerchant(@PathVariable Long merchantId) {
        followService.follow(authService.getCurrentUserId(), UdFollow.TYPE_MERCHANT, merchantId);
        return Result.success();
    }

    @DeleteMapping("/merchant/{merchantId}")
    public Result<Void> unfollowMerchant(@PathVariable Long merchantId) {
        followService.unfollow(authService.getCurrentUserId(), UdFollow.TYPE_MERCHANT, merchantId);
        return Result.success();
    }

    @PostMapping("/product/{productId}")
    public Result<Void> followProduct(@PathVariable Long productId) {
        followService.follow(authService.getCurrentUserId(), UdFollow.TYPE_PRODUCT, productId);
        return Result.success();
    }

    @DeleteMapping("/product/{productId}")
    public Result<Void> unfollowProduct(@PathVariable Long productId) {
        followService.unfollow(authService.getCurrentUserId(), UdFollow.TYPE_PRODUCT, productId);
        return Result.success();
    }
}
