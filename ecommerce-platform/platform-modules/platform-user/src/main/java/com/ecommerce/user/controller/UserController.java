package com.ecommerce.user.controller;

import com.ecommerce.api.user.dto.AddressDTO;
import com.ecommerce.api.user.dto.UpdateProfileDTO;
import com.ecommerce.api.user.vo.AddressVO;
import com.ecommerce.api.user.vo.ProfileVO;
import com.ecommerce.common.result.Result;
import com.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户控制器
 * 提供用户个人资料与收货地址相关的 REST API
 *
 * @author ecommerce
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 从 Spring Security 上下文中获取当前登录用户的ID
     *
     * @return 当前用户ID
     */
    private Long getCurrentUserId() {
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

    // ======================== 用户资料 ========================

    /**
     * 获取当前登录用户的个人资料
     *
     * @return 用户资料
     */
    @GetMapping("/profile")
    public Result<ProfileVO> getProfile() {
        Long userId = getCurrentUserId();
        log.info("获取用户资料，userId={}", userId);
        ProfileVO profile = userService.getProfile(userId);
        return Result.success(profile);
    }

    /**
     * 更新当前登录用户的个人资料
     *
     * @param dto 更新请求
     * @return 更新后的用户资料
     */
    @PutMapping("/profile")
    public Result<ProfileVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        Long userId = getCurrentUserId();
        log.info("更新用户资料，userId={}", userId);
        ProfileVO profile = userService.updateProfile(userId, dto);
        return Result.success(profile);
    }

    // ======================== 收货地址 ========================

    /**
     * 新增收货地址
     *
     * @param dto 地址请求
     * @return 创建的地址
     */
    @PostMapping("/address")
    public Result<AddressVO> createAddress(@Valid @RequestBody AddressDTO dto) {
        Long userId = getCurrentUserId();
        log.info("新增收货地址，userId={}", userId);
        AddressVO address = userService.createAddress(userId, dto);
        return Result.success(address);
    }

    /**
     * 查询当前登录用户的所有收货地址
     *
     * @return 地址列表
     */
    @GetMapping("/address/list")
    public Result<List<AddressVO>> listAddresses() {
        Long userId = getCurrentUserId();
        log.info("查询收货地址列表，userId={}", userId);
        List<AddressVO> addresses = userService.listAddresses(userId);
        return Result.success(addresses);
    }

    /**
     * 删除指定收货地址
     *
     * @param id 地址ID
     * @return 操作结果
     */
    @DeleteMapping("/address/{id}")
    public Result<Void> deleteAddress(@PathVariable("id") Long id) {
        Long userId = getCurrentUserId();
        log.info("删除收货地址，userId={}，addressId={}", userId, id);
        userService.deleteAddress(userId, id);
        return Result.success();
    }

    /**
     * 设置默认收货地址
     *
     * @param id 地址ID
     * @return 操作结果
     */
    @PutMapping("/address/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable("id") Long id) {
        Long userId = getCurrentUserId();
        log.info("设置默认收货地址，userId={}，addressId={}", userId, id);
        userService.setDefaultAddress(userId, id);
        return Result.success();
    }
}
