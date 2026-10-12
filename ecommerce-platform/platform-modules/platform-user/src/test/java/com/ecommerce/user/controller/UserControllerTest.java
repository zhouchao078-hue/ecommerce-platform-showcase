package com.ecommerce.user.controller;

import com.ecommerce.api.user.dto.AddressDTO;
import com.ecommerce.api.user.dto.UpdateProfileDTO;
import com.ecommerce.api.user.vo.AddressVO;
import com.ecommerce.api.user.vo.ProfileVO;
import com.ecommerce.framework.util.JwtUtil;
import com.ecommerce.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 集成测试
 * 使用 MockMvc 模拟 HTTP 请求，验证 API 层的请求处理逻辑
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("UserController 测试")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserService userService;

    /** JWT Bearer token for user ID 1 */
    private String authHeader;

    @BeforeEach
    void setUp() {
        // 为每个测试生成一个有效的 JWT 令牌，模拟已登录用户（userId=1）
        String token = jwtUtil.generateAccessToken(1L, "testuser",
                List.of("ROLE_USER"));
        authHeader = "Bearer " + token;
    }

    // ======================== 用户资料测试 ========================

    @Nested
    @DisplayName("获取用户资料")
    class GetProfileTests {

        @Test
        @DisplayName("正常获取用户资料")
        void shouldReturnProfile() throws Exception {
            ProfileVO profile = ProfileVO.builder()
                    .id(1L)
                    .username("testuser")
                    .phone("13800138000")
                    .email("test@example.com")
                    .avatar("https://example.com/avatar.png")
                    .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                    .build();
            when(userService.getProfile(1L)).thenReturn(profile);

            mockMvc.perform(get("/api/user/profile")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.username").value("testuser"));
        }
    }

    @Nested
    @DisplayName("更新用户资料")
    class UpdateProfileTests {

        @Test
        @DisplayName("正常更新用户名和头像")
        void shouldUpdateProfile() throws Exception {
            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setUsername("newname");
            dto.setAvatar("https://example.com/new-avatar.png");

            ProfileVO updated = ProfileVO.builder()
                    .id(1L)
                    .username("newname")
                    .phone("13800138000")
                    .email("test@example.com")
                    .avatar("https://example.com/new-avatar.png")
                    .createdAt(LocalDateTime.of(2025, 1, 1, 10, 0))
                    .build();
            when(userService.updateProfile(eq(1L), any(UpdateProfileDTO.class))).thenReturn(updated);

            mockMvc.perform(put("/api/user/profile")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.username").value("newname"))
                    .andExpect(jsonPath("$.data.avatar").value("https://example.com/new-avatar.png"));
        }

        @Test
        @DisplayName("参数校验失败：用户名为空字符串")
        void shouldRejectEmptyUsername() throws Exception {
            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setUsername("");
            dto.setAvatar("https://example.com/avatar.png");

            mockMvc.perform(put("/api/user/profile")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("参数校验失败：用户名超过32个字符")
        void shouldRejectTooLongUsername() throws Exception {
            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setUsername("a".repeat(33));

            mockMvc.perform(put("/api/user/profile")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ======================== 收货地址测试 ========================

    @Nested
    @DisplayName("新增收货地址")
    class CreateAddressTests {

        @Test
        @DisplayName("正常创建收货地址")
        void shouldCreateAddress() throws Exception {
            AddressDTO dto = AddressDTO.builder()
                    .receiverName("张三")
                    .phone("13800138000")
                    .province("广东省")
                    .city("深圳市")
                    .district("南山区")
                    .detailAddress("科技园路1号")
                    .isDefault(0)
                    .build();

            AddressVO vo = AddressVO.builder()
                    .id(1L).userId(1L).receiverName("张三")
                    .phone("13800138000").province("广东省").city("深圳市")
                    .district("南山区").detailAddress("科技园路1号")
                    .isDefault(0).createdAt(LocalDateTime.now()).build();
            when(userService.createAddress(eq(1L), any(AddressDTO.class))).thenReturn(vo);

            mockMvc.perform(post("/api/user/address")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.receiverName").value("张三"));
        }

        @Test
        @DisplayName("参数校验失败：收货人姓名为空")
        void shouldRejectEmptyReceiverName() throws Exception {
            AddressDTO dto = AddressDTO.builder()
                    .receiverName("").phone("13800138000")
                    .province("广东省").city("深圳市").district("南山区")
                    .detailAddress("科技园路1号").build();

            mockMvc.perform(post("/api/user/address")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("参数校验失败：手机号格式错误")
        void shouldRejectInvalidPhone() throws Exception {
            AddressDTO dto = AddressDTO.builder()
                    .receiverName("张三").phone("12345")
                    .province("广东省").city("深圳市").district("南山区")
                    .detailAddress("科技园路1号").build();

            mockMvc.perform(post("/api/user/address")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("查询收货地址列表")
    class ListAddressesTests {

        @Test
        @DisplayName("正常查询地址列表")
        void shouldReturnAddressList() throws Exception {
            AddressVO addr1 = AddressVO.builder()
                    .id(1L).userId(1L).receiverName("张三")
                    .phone("13800138000").province("广东省").city("深圳市")
                    .district("南山区").detailAddress("科技园路1号")
                    .isDefault(1).createdAt(LocalDateTime.now()).build();
            AddressVO addr2 = AddressVO.builder()
                    .id(2L).userId(1L).receiverName("李四")
                    .phone("13900139000").province("广东省").city("广州市")
                    .district("天河区").detailAddress("体育西路100号")
                    .isDefault(0).createdAt(LocalDateTime.now()).build();

            when(userService.listAddresses(1L)).thenReturn(Arrays.asList(addr1, addr2));

            mockMvc.perform(get("/api/user/address/list")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].receiverName").value("张三"))
                    .andExpect(jsonPath("$.data[1].receiverName").value("李四"));
        }

        @Test
        @DisplayName("无地址时返回空列表")
        void shouldReturnEmptyListWhenNoAddresses() throws Exception {
            when(userService.listAddresses(1L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/user/address/list")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    @DisplayName("删除收货地址")
    class DeleteAddressTests {

        @Test
        @DisplayName("正常删除收货地址")
        void shouldDeleteAddress() throws Exception {
            doNothing().when(userService).deleteAddress(1L, 10L);

            mockMvc.perform(delete("/api/user/address/10")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("设置默认收货地址")
    class SetDefaultAddressTests {

        @Test
        @DisplayName("正常设置默认地址")
        void shouldSetDefaultAddress() throws Exception {
            doNothing().when(userService).setDefaultAddress(1L, 10L);

            mockMvc.perform(put("/api/user/address/10/default")
                            .header("Authorization", authHeader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }
}
