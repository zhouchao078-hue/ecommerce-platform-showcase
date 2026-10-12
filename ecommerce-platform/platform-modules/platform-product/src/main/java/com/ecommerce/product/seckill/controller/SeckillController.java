package com.ecommerce.product.seckill.controller;

import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.seckill.model.SeckillProductCreateRequest;
import com.ecommerce.product.seckill.model.SeckillProductVO;
import com.ecommerce.product.seckill.model.SeckillPurchaseVO;
import com.ecommerce.product.seckill.service.SeckillProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {
    private final SeckillProductService seckillProductService;
    private final AuthService authService;
    private final CategoryService categoryService;

    @GetMapping("/products")
    public Result<List<SeckillProductVO>> list() {
        return Result.success(seckillProductService.listAvailable());
    }

    @GetMapping("/products/{id}")
    public Result<SeckillProductVO> get(@PathVariable Long id) {
        return Result.success(seckillProductService.getAvailable(id));
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/products")
    public Result<Void> create(@RequestBody SeckillProductCreateRequest request) {
        Long userId = authService.getCurrentUserId();
        seckillProductService.create(categoryService.getMerchantIdByUserId(userId), request);
        return Result.success();
    }

    @PostMapping("/{id}/purchase")
    public Result<SeckillPurchaseVO> purchase(@PathVariable Long id) {
        return Result.success(seckillProductService.purchase(id, authService.getCurrentUserId()));
    }
}
