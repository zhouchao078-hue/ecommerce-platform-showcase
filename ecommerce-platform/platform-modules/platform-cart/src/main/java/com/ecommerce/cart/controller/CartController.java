package com.ecommerce.cart.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.api.cart.dto.AddCartDTO;
import com.ecommerce.api.cart.vo.CartVO;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ecommerce.auth.service.AuthService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final AuthService authService;
    private final CartService cartService;

    

    @GetMapping
    public Result<IPage<CartVO>> listCart(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = authService.getCurrentUserId();
        return Result.success(cartService.listCart(userId, pageNum, pageSize));
    }

    @PostMapping
    public Result<Void> addCart(@RequestBody @Valid AddCartDTO dto) {
        Long userId = authService.getCurrentUserId();
        cartService.addCart(userId, dto.getProductId(), dto.getSkuId(), dto.getQuantity());
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> updateCartQuantity(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer quantity) {
        Long userId = authService.getCurrentUserId();
        cartService.updateCartQuantity(id, userId, quantity);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCart(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        cartService.deleteCart(id, userId);
        return Result.success();
    }
}
