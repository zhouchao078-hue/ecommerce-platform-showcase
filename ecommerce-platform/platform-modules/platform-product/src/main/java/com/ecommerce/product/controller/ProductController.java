package com.ecommerce.product.controller;


import com.ecommerce.common.result.Result;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ecommerce.auth.service.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {
    private final AuthService authService;
    private final ProductService productService;
    private final CategoryService categoryService;


    @GetMapping("/products/public")
    public Result<List<PdProduct>> listAllProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        List<PdProduct> list = productService.listAllProducts(categoryId, pageNum, pageSize);
        return Result.success(list);
    }

    // ===== 以下为商家自营接口：必须是已开通的商家（ROLE_MERCHANT） =====

    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/products")
    public Result<List<PdProduct>> listProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        List<PdProduct> list = productService.listproducts(merchantId, categoryId, pageNum, pageSize);
        return Result.success(list);
    }
    @GetMapping("/products/{id}")
    public Result<PdProduct> getProductById(@PathVariable Long id){
        PdProduct product = productService.getProductById(id);
        return Result.success(product);
    }
    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/products")
    public Result<Void> addProduct(@RequestBody PdProduct pdProduct) {
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        pdProduct.setMerchantId(merchantId);
        productService.addproduct(pdProduct);
        return Result.success();
    }
    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/products/{id}")
    public Result<Void> updateProduct(@PathVariable Long id, @RequestBody PdProduct pdProduct) {
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        pdProduct.setId(id);
        productService.updateproduct(pdProduct,merchantId);
        return Result.success();
    }
    @PreAuthorize("hasRole('MERCHANT')")
    @DeleteMapping("/products/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        productService.deleteproduct(id,merchantId);
        return Result.success();
    }
    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/products/{id}/status")
    public Result<Void> updateProductStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Long userId = authService.getCurrentUserId();
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        productService.updateStatus(id, body.get("status"), merchantId);
        return Result.success();
    }
    @GetMapping("/products/search")
    public Result<List<PdProduct>> searchProducts( @RequestParam("keyword") String Keyword,
                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "10") Integer pageSize){

        List<PdProduct>list = productService.searchProducts(Keyword,pageNum,pageSize);
        return Result.success(list);
    }

}
