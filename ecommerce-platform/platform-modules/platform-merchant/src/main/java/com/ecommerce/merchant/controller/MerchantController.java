package com.ecommerce.merchant.controller;

import com.ecommerce.api.merchant.dto.ApplyMerchantDTO;
import com.ecommerce.api.merchant.vo.MerchantStatusVO;
import com.ecommerce.common.result.Result;
import com.ecommerce.merchant.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 商家申请
 */


@Slf4j
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {
    private final MerchantService merchantService;

    /**
     * 从 Spring Security 上下文中获取当前登录用户的ID
     *
     * @return 当前用户ID
     */
    private Long getMerchantUserId(){
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        // 兼容整数类型（JWT中可能存为Integer）
        if (principal instanceof Integer) {
            return ((Integer) principal).longValue();
        }
        return Long.valueOf(principal.toString());
    }
    /**
     * 检查是否为商家
     */
    @GetMapping("/status")
    public Result<MerchantStatusVO> getMerchantStatus() {
        Long userId = getMerchantUserId();
        log.info("获取用户资料，userId={}", userId);
        MerchantStatusVO status = merchantService.getStatus(userId);
        return Result.success(status);
    }
    /**
     *
     *
     */
    @PostMapping("/apply")
    public Result<Void> applyMerchant(@RequestBody @Valid ApplyMerchantDTO dto) {
        Long userId = getMerchantUserId();
        merchantService.apply(userId, dto);
        return Result.success();
    }

}
