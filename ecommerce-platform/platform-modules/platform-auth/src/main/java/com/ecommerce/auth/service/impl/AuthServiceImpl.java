package com.ecommerce.auth.service.impl;

import com.ecommerce.api.auth.dto.LoginDTO;
import com.ecommerce.api.auth.dto.RegisterDTO;
import com.ecommerce.api.auth.vo.LoginVO;
import com.ecommerce.api.auth.vo.UserInfoVO;
import com.ecommerce.auth.mapper.SysRoleMapper;
import com.ecommerce.auth.mapper.SysUserMapper;
import com.ecommerce.auth.mapper.SysUserRoleMapper;
import com.ecommerce.auth.model.SysRole;
import com.ecommerce.auth.model.SysUser;
import com.ecommerce.auth.model.SysUserRole;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.framework.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务实现类
 * 实现登录、注册、刷新令牌、获取当前用户等核心认证功能
 *
 * @author ecommerce
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /** 默认用户角色编码 */
    private static final String ROLE_USER = "ROLE_USER";

    /** 用户正常状态 */
    private static final int STATUS_ENABLED = 1;

    /** 访问令牌过期时间（秒） */
    private static final long ACCESS_TOKEN_EXPIRES_IN = 30 * 60L;

    /**
     * 用户注册
     * 1. 校验用户名唯一性
     * 2. 校验手机号码唯一性
     * 3. BCrypt 加密密码
     * 4. 插入用户记录
     * 5. 分配 ROLE_USER 角色
     * 6. 生成令牌并返回
     *
     * @param dto 注册请求参数
     * @return 登录响应（含令牌）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto) {
        log.info("用户注册请求: username={}", dto.getUsername());

        // 1. 校验用户名唯一性
        SysUser existByUsername = sysUserMapper.selectByUsername(dto.getUsername());
        if (existByUsername != null) {
            log.warn("注册失败：用户名已存在, username={}", dto.getUsername());
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        // 2. 校验手机号码唯一性
        if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
            SysUser existByPhone = sysUserMapper.selectByPhone(dto.getPhone());
            if (existByPhone != null) {
                log.warn("注册失败：手机号码已被注册, phone={}", dto.getPhone());
                throw new BizException(ErrorCode.PHONE_EXISTS);
            }
        }

        // 3. BCrypt 加密密码
        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        // 4. 构建并插入用户记录
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(encodedPassword);
        user.setPhone(dto.getPhone());
        user.setStatus(STATUS_ENABLED);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        int inserted = sysUserMapper.insert(user);
        if (inserted <= 0) {
            log.error("注册失败：用户插入数据库失败, username={}", dto.getUsername());
            throw new BizException(ErrorCode.INTERNAL_ERROR, "用户注册失败，请稍后重试");
        }
        log.info("用户创建成功: userId={}, username={}", user.getId(), user.getUsername());

        // 5. 查询 ROLE_USER 角色并分配
        SysRole userRole = sysRoleMapper.selectByCode(ROLE_USER);
        if (userRole == null) {
            log.error("注册失败：ROLE_USER 角色不存在");
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        SysUserRole userRoleRel = new SysUserRole();
        userRoleRel.setUserId(user.getId());
        userRoleRel.setRoleId(userRole.getId());
        sysUserRoleMapper.insert(userRoleRel);
        log.info("用户角色分配成功: userId={}, roleCode={}", user.getId(), ROLE_USER);

        // 6. 生成令牌
        List<String> roles = Collections.singletonList(ROLE_USER);
        return buildLoginVO(user, roles);
    }

    /**
     * 用户登录
     * 1. 先尝试按用户名查找用户
     * 2. 若未找到，再尝试按手机号码查找
     * 3. 验证 BCrypt 密码
     * 4. 检查账号状态是否为启用
     * 5. 查询用户角色
     * 6. 生成令牌并返回
     *
     * @param dto 登录请求参数
     * @return 登录响应（含令牌）
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        log.info("用户登录请求: account={}", dto.getUsername());

        // 1. 先尝试按用户名查找用户
        SysUser user = sysUserMapper.selectByUsername(dto.getUsername());

        // 2. 若未找到，再尝试按手机号码查找
        if (user == null) {
            user = sysUserMapper.selectByPhone(dto.getUsername());
        }

        // 3. 用户不存在
        if (user == null) {
            log.warn("登录失败：用户不存在, account={}", dto.getUsername());
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        // 4. 验证密码
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败：密码错误, account={}", dto.getUsername());
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        // 5. 检查账号状态
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            log.warn("登录失败：账号已被禁用, account={}", dto.getUsername());
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }


        // 6. 查询用户角色
        List<SysRole> sysRoles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<String> roles = sysRoles.stream()
                .map(SysRole::getCode)
                .collect(Collectors.toList());
        log.info("用户登录成功: userId={}, username={}, roles={}", user.getId(), user.getUsername(), roles);

        // 6. 登录次数+1，并更新数据库
        user.setLoginCount(user.getLoginCount() == null ? 1 : user.getLoginCount() + 1);
        sysUserMapper.updateById(user);
        log.info("登录次数：{}",user.getLoginCount());
        // 7. 生成令牌并返回
        return buildLoginVO(user, roles);
    }

    /**
     * 刷新令牌
     * 1. 验证刷新令牌有效性
     * 2. 获取用户ID
     * 3. 检查用户是否存在且状态正常
     * 4. 查询用户角色
     * 5. 生成新的访问令牌和刷新令牌并返回
     *
     * @param refreshToken 刷新令牌
     * @return 登录响应（含新令牌）
     */
    @Override
    public LoginVO refresh(String refreshToken) {
        log.info("刷新令牌请求");

        // 1. 验证刷新令牌
        if (!jwtUtil.validateToken(refreshToken)) {
            log.warn("刷新令牌无效或已过期");
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 2. 获取用户ID
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        if (userId == null) {
            log.warn("刷新令牌中无法解析用户ID");
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 3. 检查用户是否存在且状态正常
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            log.warn("刷新令牌对应的用户不存在: userId={}", userId);
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            log.warn("刷新令牌对应的用户已被禁用: userId={}", userId);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. 查询用户角色
        List<SysRole> sysRoles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<String> roles = sysRoles.stream()
                .map(SysRole::getCode)
                .collect(Collectors.toList());

        log.info("刷新令牌成功: userId={}", userId);

        // 5. 生成新令牌并返回
        return buildLoginVO(user, roles);
    }

    /**
     * 获取当前登录用户信息
     * 根据用户ID查询用户详情及角色信息
     *
     * @param userId 用户ID
     * @return 用户信息VO
     */
    @Override
    public UserInfoVO getCurrentUser(Long userId) {
        log.info("获取当前用户信息: userId={}", userId);

        // 1. 查询用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            log.warn("获取当前用户失败：用户不存在, userId={}", userId);
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 查询用户角色
        List<SysRole> sysRoles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<String> roles = sysRoles.stream()
                .map(SysRole::getCode)
                .collect(Collectors.toList());

        log.info("获取当前用户信息成功: userId={}, username={}", userId, user.getUsername());

        // 3. 构建 UserInfoVO
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .loginCount(user.getLoginCount())
                .build();
    }

    /**
     * 构建登录响应VO
     * 生成访问令牌和刷新令牌，组装返回数据
     *
     * @param user  用户实体
     * @param roles 角色编码列表
     * @return LoginVO
     */
    private LoginVO buildLoginVO(SysUser user, List<String> roles) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(ACCESS_TOKEN_EXPIRES_IN)
                .loginCount(user.getLoginCount())
                .build();
    }    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash()))
            throw new BizException(ErrorCode.BAD_REQUEST, "原密码错误");
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
    }

    @Override
    public SysUser getUserById(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
        return user;
    }

    @Override
    public SysUser updateUserProfile(Long userId, String username, String avatar) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
        if (username != null) user.setUsername(username);
        if (avatar != null) user.setAvatar(avatar);
        sysUserMapper.updateById(user);
        return user;
    }

    @Override
    public Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Integer) return ((Integer) principal).longValue();
        return Long.valueOf(principal.toString());
    }

    @Override
    public Long getCurrentUserIdOrNull() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Integer) return ((Integer) principal).longValue();
        // 未登录时 principal 是字符串 "anonymousUser"，不能直接转 Long
        try {
            return Long.valueOf(principal.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
