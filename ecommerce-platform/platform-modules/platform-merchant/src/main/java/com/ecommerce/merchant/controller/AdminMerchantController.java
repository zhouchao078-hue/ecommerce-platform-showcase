package com.ecommerce.merchant.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.result.Result;
import com.ecommerce.merchant.model.MdMerchant;
import com.ecommerce.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员 - 商家管理接口
 * <p>仅 ROLE_ADMIN 角色可访问（@PreAuthorize 方法级鉴权）</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/merchants")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantService merchantService;

    /**
     * 商家列表（可按状态筛选）
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<IPage<MdMerchant>> listMerchants(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(merchantService.listMerchants(status, pageNum, pageSize));
    }

    /**
     * 审核商家（0待审核 → 1通过 / 2拒绝）
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/review")
    public Result<Void> reviewMerchant(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        merchantService.reviewMerchant(id, body.get("status"));
        return Result.success();
    }
}
