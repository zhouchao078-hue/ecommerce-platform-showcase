package com.ecommerce.product.controller;

import com.ecommerce.api.product.dto.Categorydto;
import com.ecommerce.common.result.Result;
import com.ecommerce.product.model.PdCategory;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ecommerce.auth.service.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategoryController {
    private final AuthService authService;
    private final CategoryService categoryService;

    

    // ===== 以下为商家自营接口：必须是已开通的商家（ROLE_MERCHANT） =====

    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/categories")
    public Result<List<PdCategory>> listCategories() {
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        List<PdCategory> list = categoryService.listCategories(merchantId);
        return Result.success(list);
    }

    @GetMapping("/categories/public")
    public Result<List<PdCategory>> listAllCategories() {
        List<PdCategory> list = categoryService.listAllCategories();
        return Result.success(list);
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/categories")
    public Result<Void> createCategory(@RequestBody Categorydto dto) {
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        categoryService.createCategory(merchantId, dto.getName(), dto.getParentId(), dto.getSortOrder());
        return Result.success();
    }
    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id,@RequestBody Categorydto dto) {
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        String name = dto.getName();
        Long parentId = dto.getParentId();
        Integer sortOrder = dto.getSortOrder();
        categoryService.updateCategory(id, merchantId, name, parentId, sortOrder);
        return Result.success();
    }
    @PreAuthorize("hasRole('MERCHANT')")
    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id){
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        categoryService.deleteCategory(id, merchantId);
        return Result.success();
    }
}
