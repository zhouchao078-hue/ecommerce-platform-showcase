package com.ecommerce.auth.controller;

import com.ecommerce.api.auth.dto.LoginDTO;
import com.ecommerce.api.auth.dto.RegisterDTO;
import com.ecommerce.api.auth.vo.LoginVO;
import com.ecommerce.api.auth.vo.UserInfoVO;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.framework.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证控制器集成测试
 * 使用 SpringBootTest 加载完整上下文，MockMvc 发送请求
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController 集成测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 测试配置：使用 Mock 的 AuthService 代替真实实现，隔离数据库依赖
     */
    @TestConfiguration
    static class MockAuthServiceConfig {

        @Bean
        @Primary
        public AuthService authService() {
            return mock(AuthService.class);
        }
    }

    @Autowired
    private AuthService authService;

    // ==================== 登录接口测试 ====================

    @Test
    @DisplayName("POST /api/auth/login - 使用合法凭据登录，应返回200和令牌信息")
    void testLogin_Success() throws Exception {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("password123");

        LoginVO mockVO = LoginVO.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .expiresIn(1800L)
                .build();

        when(authService.login(any(LoginDTO.class))).thenReturn(mockVO);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800));
    }

    @Test
    @DisplayName("POST /api/auth/login - 密码为空时应返回400参数校验错误")
    void testLogin_MissingPassword() throws Exception {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword(""); // 空密码触发 @NotBlank 校验

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDTO)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 注册接口测试 ====================

    @Test
    @DisplayName("POST /api/auth/register - 注册新用户成功，应返回200和令牌信息")
    void testRegister_Success() throws Exception {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("newuser");
        registerDTO.setPassword("password123");
        registerDTO.setPhone("13600136000");

        LoginVO mockVO = LoginVO.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .expiresIn(1800L)
                .build();

        when(authService.register(any(RegisterDTO.class))).thenReturn(mockVO);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDTO)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"));
    }

    @Test
    @DisplayName("POST /api/auth/register - 用户名为空时应返回400参数校验错误")
    void testRegister_MissingUsername() throws Exception {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername(""); // 空用户名
        registerDTO.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDTO)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/auth/register - 密码长度不足6位时应返回400参数校验错误")
    void testRegister_ShortPassword() throws Exception {
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("newuser");
        registerDTO.setPassword("12345"); // 密码长度不足6位

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDTO)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 获取当前用户接口测试 ====================

    @Test
    @DisplayName("GET /api/auth/me - 携带有效令牌时应返回200和用户信息")
    void testGetCurrentUser_Success() throws Exception {
        // 生成一个有效的 JWT 访问令牌
        String accessToken = jwtUtil.generateAccessToken(1L, "testuser", Collections.singletonList("ROLE_USER"));

        UserInfoVO mockUserInfo = UserInfoVO.builder()
                .id(1L)
                .username("testuser")
                .phone("13800138000")
                .email("test@example.com")
                .status(1)
                .roles(Collections.singletonList("ROLE_USER"))
                .createdAt(LocalDateTime.now())
                .build();

        when(authService.getCurrentUserId()).thenReturn(1L);
        when(authService.getCurrentUser(1L)).thenReturn(mockUserInfo);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_USER"));
    }

    @Test
    @DisplayName("GET /api/auth/me - 未携带令牌时应返回401")
    void testGetCurrentUser_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    // ==================== 刷新令牌接口测试 ====================

    @Test
    @DisplayName("POST /api/auth/refresh - 携带有效刷新令牌时应返回200和新令牌")
    void testRefresh_Success() throws Exception {
        String refreshToken = jwtUtil.generateRefreshToken(1L, "testuser");
        String requestBody = "{\"refreshToken\":\"" + refreshToken + "\"}";

        LoginVO mockVO = LoginVO.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .expiresIn(1800L)
                .build();

        when(authService.refresh(anyString())).thenReturn(mockVO);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 刷新令牌为空时应返回400参数校验错误")
    void testRefresh_EmptyToken() throws Exception {
        String requestBody = "{\"refreshToken\":\"\"}";

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
