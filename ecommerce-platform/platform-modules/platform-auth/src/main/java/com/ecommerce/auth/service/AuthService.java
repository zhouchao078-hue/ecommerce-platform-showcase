package com.ecommerce.auth.service;

import com.ecommerce.api.auth.dto.LoginDTO;
import com.ecommerce.api.auth.dto.RefreshDTO;
import com.ecommerce.api.auth.dto.RegisterDTO;
import com.ecommerce.api.auth.vo.LoginVO;
import com.ecommerce.api.auth.vo.UserInfoVO;
import com.ecommerce.auth.model.SysUser;

/**
 * 认证服务接口
 * 提供登录、注册、刷新令牌、获取当前用户等核心认证功能
 *
 * @author ecommerce
 */
public interface AuthService {

    /**
     * 用户登录
     * 支持用户名或手机号码 + 密码登录
     *
     * @param dto 登录请求参数（包含账号和密码）
     * @return 登录成功返回令牌信息
     */
    LoginVO login(LoginDTO dto);

    /**
     * 用户注册
     * 创建新用户，分配默认角色（ROLE_USER），并返回令牌
     *
     * @param dto 注册请求参数（包含用户名、密码、手机号）
     * @return 注册成功返回令牌信息
     */
    LoginVO register(RegisterDTO dto);

    /**
     * 刷新令牌
     * 使用刷新令牌获取新的访问令牌
     *
     * @param refreshToken 刷新令牌字符串
     * @return 包含新访问令牌和刷新令牌的响应
     */
    LoginVO refresh(String refreshToken);

    /**
     * 获取当前登录用户信息
     * 根据用户ID查询用户详情及角色信息
     *
     * @param userId 用户ID
     * @return 用户信息VO
     */
    UserInfoVO getCurrentUser(Long userId);

    /** 修改密码 */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 供其他模块查询用户 */
    SysUser getUserById(Long userId);

    /** 供其他模块更新用户资料 */
    SysUser updateUserProfile(Long userId, String username, String avatar);

    /** 获取当前登录用户ID */
    Long getCurrentUserId();

    /**
     * 取当前登录用户 ID，未登录时返回 null。
     * 用于「登录可选」的公开接口（例如商家主页要显示是否已关注，
     * 但未登录用户也应该能看到这个页面）。
     */
    Long getCurrentUserIdOrNull();
}
