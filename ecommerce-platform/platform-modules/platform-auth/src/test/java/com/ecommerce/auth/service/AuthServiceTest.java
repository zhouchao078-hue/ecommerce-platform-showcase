package com.ecommerce.auth.service;

import com.ecommerce.api.auth.dto.LoginDTO;
import com.ecommerce.api.auth.dto.RegisterDTO;
import com.ecommerce.api.auth.vo.LoginVO;
import com.ecommerce.api.auth.vo.UserInfoVO;
import com.ecommerce.auth.mapper.SysRoleMapper;
import com.ecommerce.auth.mapper.SysUserMapper;
import com.ecommerce.auth.mapper.SysUserRoleMapper;
import com.ecommerce.auth.model.SysRole;
import com.ecommerce.auth.model.SysUser;
import com.ecommerce.auth.service.impl.AuthServiceImpl;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.framework.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务层单元测试
 * 使用 Mockito 模拟依赖进行隔离测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试")
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    private SysUser mockUser;
    private SysRole mockRole;

    @BeforeEach
    void setUp() {
        mockUser = new SysUser();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setPasswordHash("$2a$10$encodedpasswordhash");
        mockUser.setPhone("13800138000");
        mockUser.setEmail("test@example.com");
        mockUser.setStatus(1);
        mockUser.setCreatedAt(LocalDateTime.now());
        mockUser.setUpdatedAt(LocalDateTime.now());

        mockRole = new SysRole();
        mockRole.setId(1L);
        mockRole.setName("普通用户");
        mockRole.setCode("ROLE_USER");
    }

    // ==================== 注册测试 ====================

    @Test
    @DisplayName("注册成功 - 新用户注册应返回令牌信息")
    void testRegister_Success() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("password123");
        dto.setPhone("13600136000");

        when(sysUserMapper.selectByUsername("newuser")).thenReturn(null);
        when(sysUserMapper.selectByPhone("13600136000")).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser u = invocation.getArgument(0);
            u.setId(10L);
            return 1;
        });
        when(sysRoleMapper.selectByCode("ROLE_USER")).thenReturn(mockRole);
        when(sysUserRoleMapper.insert(any(com.ecommerce.auth.model.SysUserRole.class))).thenReturn(1);
        when(jwtUtil.generateAccessToken(eq(10L), eq("newuser"), anyList())).thenReturn("access-token-xxx");
        when(jwtUtil.generateRefreshToken(10L, "newuser")).thenReturn("refresh-token-xxx");

        LoginVO result = authService.register(dto);

        assertNotNull(result);
        assertEquals("access-token-xxx", result.getAccessToken());
        assertEquals("refresh-token-xxx", result.getRefreshToken());
        assertEquals(30 * 60L, result.getExpiresIn());

        verify(sysUserMapper).insert(any(SysUser.class));
        verify(sysUserRoleMapper).insert(any(com.ecommerce.auth.model.SysUserRole.class));
        verify(sysRoleMapper).selectByCode("ROLE_USER");
    }

    @Test
    @DisplayName("注册失败 - 用户名已存在时应抛出 BizException")
    void testRegister_DuplicateUsername() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("testuser");
        dto.setPassword("password123");

        when(sysUserMapper.selectByUsername("testuser")).thenReturn(mockUser);

        BizException exception = assertThrows(BizException.class, () -> authService.register(dto));
        assertTrue(exception.getMessage().contains("用户名已存在"));

        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("注册失败 - 手机号码已被注册时应抛出 BizException")
    void testRegister_DuplicatePhone() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("password123");
        dto.setPhone("13800138000");

        when(sysUserMapper.selectByUsername("newuser")).thenReturn(null);
        when(sysUserMapper.selectByPhone("13800138000")).thenReturn(mockUser);

        BizException exception = assertThrows(BizException.class, () -> authService.register(dto));
        assertTrue(exception.getMessage().contains("手机号已存在"));
    }

    // ==================== 登录测试 ====================

    @Test
    @DisplayName("登录成功 - 使用用户名和正确密码登录应返回令牌")
    void testLogin_Success_ByUsername() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("password123");

        when(sysUserMapper.selectByUsername("testuser")).thenReturn(mockUser);
        when(passwordEncoder.matches("password123", mockUser.getPasswordHash())).thenReturn(true);
        when(sysRoleMapper.selectRolesByUserId(1L)).thenReturn(Collections.singletonList(mockRole));
        when(jwtUtil.generateAccessToken(eq(1L), eq("testuser"), anyList())).thenReturn("access-token-xxx");
        when(jwtUtil.generateRefreshToken(1L, "testuser")).thenReturn("refresh-token-xxx");

        LoginVO result = authService.login(dto);

        assertNotNull(result);
        assertEquals("access-token-xxx", result.getAccessToken());
        assertEquals("refresh-token-xxx", result.getRefreshToken());
    }

    @Test
    @DisplayName("登录成功 - 使用手机号码登录（用户名查找失败后fallback到手机号）")
    void testLogin_Success_ByPhone() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("13800138000");
        dto.setPassword("password123");

        when(sysUserMapper.selectByUsername("13800138000")).thenReturn(null);
        when(sysUserMapper.selectByPhone("13800138000")).thenReturn(mockUser);
        when(passwordEncoder.matches("password123", mockUser.getPasswordHash())).thenReturn(true);
        when(sysRoleMapper.selectRolesByUserId(1L)).thenReturn(Collections.singletonList(mockRole));
        when(jwtUtil.generateAccessToken(eq(1L), eq("testuser"), anyList())).thenReturn("access-token-xxx");
        when(jwtUtil.generateRefreshToken(1L, "testuser")).thenReturn("refresh-token-xxx");

        LoginVO result = authService.login(dto);

        assertNotNull(result);
        verify(sysUserMapper).selectByUsername("13800138000");
        verify(sysUserMapper).selectByPhone("13800138000");
    }

    @Test
    @DisplayName("登录失败 - 用户名或密码错误时应抛出 BizException")
    void testLogin_BadCredentials() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("wrongpassword");

        when(sysUserMapper.selectByUsername("testuser")).thenReturn(mockUser);
        when(passwordEncoder.matches("wrongpassword", mockUser.getPasswordHash())).thenReturn(false);

        BizException exception = assertThrows(BizException.class, () -> authService.login(dto));
        assertTrue(exception.getMessage().contains("用户名或密码错误"));
    }

    @Test
    @DisplayName("登录失败 - 账号已被禁用时应抛出 BizException")
    void testLogin_AccountDisabled() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("password123");

        mockUser.setStatus(0);
        when(sysUserMapper.selectByUsername("testuser")).thenReturn(mockUser);
        when(passwordEncoder.matches("password123", mockUser.getPasswordHash())).thenReturn(true);

        BizException exception = assertThrows(BizException.class, () -> authService.login(dto));
        assertTrue(exception.getMessage().contains("已被禁用"));
    }

    // ==================== 获取当前用户测试 ====================

    @Test
    @DisplayName("获取当前用户信息成功 - 根据用户ID查询用户详情和角色")
    void testGetCurrentUser_Success() {
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);
        when(sysRoleMapper.selectRolesByUserId(1L)).thenReturn(Collections.singletonList(mockRole));

        UserInfoVO result = authService.getCurrentUser(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("testuser", result.getUsername());
        assertEquals("13800138000", result.getPhone());
        assertEquals("test@example.com", result.getEmail());
        assertEquals(1, result.getRoles().size());
        assertTrue(result.getRoles().contains("ROLE_USER"));
    }

    @Test
    @DisplayName("获取当前用户信息失败 - 用户不存在时应抛出 BizException")
    void testGetCurrentUser_NotFound() {
        when(sysUserMapper.selectById(999L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> authService.getCurrentUser(999L));
        assertTrue(exception.getMessage().contains("用户不存在"));
    }

    // ==================== 刷新令牌测试 ====================

    @Test
    @DisplayName("刷新令牌成功 - 有效刷新令牌应返回新令牌")
    void testRefresh_Success() {
        when(jwtUtil.validateToken("valid-refresh-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("valid-refresh-token")).thenReturn(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);
        when(sysRoleMapper.selectRolesByUserId(1L)).thenReturn(Collections.singletonList(mockRole));
        when(jwtUtil.generateAccessToken(eq(1L), eq("testuser"), anyList())).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(1L, "testuser")).thenReturn("new-refresh-token");

        LoginVO result = authService.refresh("valid-refresh-token");

        assertNotNull(result);
        assertEquals("new-access-token", result.getAccessToken());
        assertEquals("new-refresh-token", result.getRefreshToken());
    }

    @Test
    @DisplayName("刷新令牌失败 - 无效令牌应抛出 BizException")
    void testRefresh_InvalidToken() {
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);

        BizException exception = assertThrows(BizException.class, () -> authService.refresh("invalid-token"));
        assertTrue(exception.getMessage().contains("刷新令牌无效"));
    }
}
