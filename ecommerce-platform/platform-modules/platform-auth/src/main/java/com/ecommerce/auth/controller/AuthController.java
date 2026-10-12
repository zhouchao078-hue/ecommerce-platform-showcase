package com.ecommerce.auth.controller;

import com.ecommerce.api.auth.dto.LoginDTO;
import com.ecommerce.api.auth.dto.RefreshDTO;
import com.ecommerce.api.auth.dto.RegisterDTO;
import com.ecommerce.api.auth.vo.LoginVO;
import com.ecommerce.api.auth.vo.UserInfoVO;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * 认证控制器
 * 提供用户注册、登录、刷新令牌、获取当前用户等 REST API 接口
 *
 * @author ecommerce
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册接口
     *
     * @param dto 注册请求参数
     * @return 注册成功返回令牌信息
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
        log.info("收到注册请求: username={}", dto.getUsername());
        LoginVO vo = authService.register(dto);
        return Result.success(vo);
    }

    /**
     * 用户登录接口
     * 支持用户名或手机号码 + 密码登录
     *
     * @param dto 登录请求参数
     * @return 登录成功返回令牌信息
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        log.info("收到登录请求: account={}", dto.getUsername());
        LoginVO vo = authService.login(dto);
        return Result.success(vo);
    }

    /**
     * 刷新令牌接口
     * 使用刷新令牌获取新的访问令牌
     *
     * @param dto 刷新令牌请求参数
     * @return 新的令牌信息
     */
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        log.info("收到刷新令牌请求");
        LoginVO vo = authService.refresh(dto.getRefreshToken());
        return Result.success(vo);
    }

    /**
     * 获取当前登录用户信息接口
     * 从 SecurityContextHolder 中获取当前认证用户的ID，
     * 然后查询用户详细信息及角色
     *
     * @return 当前用户详细信息
     */
    

    /**
     * 修改密码接口
     * 校验原密码后更新为新密码
     *
     * @param body 请求体（oldPassword / newPassword）
     * @return 修改结果
     */
    @PutMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        Long userId = authService.getCurrentUserId();
        authService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserInfoVO> currentUser() {
        Long userId = authService.getCurrentUserId();
        log.info("获取当前用户信息: userId={}", userId);
        UserInfoVO vo = authService.getCurrentUser(userId);
        return Result.success(vo);
    }
}
