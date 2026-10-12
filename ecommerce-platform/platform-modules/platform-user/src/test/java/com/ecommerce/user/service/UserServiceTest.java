package com.ecommerce.user.service;

import com.ecommerce.api.user.dto.AddressDTO;
import com.ecommerce.api.user.dto.UpdateProfileDTO;
import com.ecommerce.api.user.vo.AddressVO;
import com.ecommerce.api.user.vo.ProfileVO;
import com.ecommerce.auth.model.SysUser;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.user.mapper.UdAddressMapper;
import com.ecommerce.user.model.UdAddress;
import com.ecommerce.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试
 * 使用 Mockito 模拟 Mapper 层，验证业务逻辑正确性
 *
 * @author ecommerce
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 测试")
class UserServiceTest {

    @Mock
    private UdAddressMapper addressMapper;

    @Mock
    private AuthService authService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void injectAuthService() {
        // AuthService 是非 final 的 @Autowired 字段，@InjectMocks 无法自动注入，手动补齐
        ReflectionTestUtils.setField(userService, "AuthService", authService);
    }

    // ======================== 用户资料测试 ========================

    @Nested
    @DisplayName("获取用户资料")
    class GetProfileTests {

        @Test
        @DisplayName("正常获取用户资料")
        void shouldReturnProfileWhenUserExists() {
            SysUser user = buildUser(1L, "testuser");
            when(authService.getUserById(1L)).thenReturn(user);

            ProfileVO profile = userService.getProfile(1L);

            assertThat(profile).isNotNull();
            assertThat(profile.getId()).isEqualTo(1L);
            assertThat(profile.getUsername()).isEqualTo("testuser");
            assertThat(profile.getPhone()).isEqualTo("13800138000");
        }

        @Test
        @DisplayName("用户不存在时抛出 BizException")
        void shouldThrowBizExceptionWhenUserNotFound() {
            when(authService.getUserById(999L)).thenThrow(new BizException(ErrorCode.USER_NOT_FOUND));

            assertThatThrownBy(() -> userService.getProfile(999L))
                    .isInstanceOf(BizException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("更新用户资料")
    class UpdateProfileTests {

        @Test
        @DisplayName("正常更新用户名和头像")
        void shouldUpdateProfileSuccessfully() {
            SysUser updated = buildUser(1L, "newname");
            updated.setAvatar("https://example.com/avatar.png");
            when(authService.updateUserProfile(1L, "newname", "https://example.com/avatar.png")).thenReturn(updated);

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setUsername("newname");
            dto.setAvatar("https://example.com/avatar.png");

            ProfileVO result = userService.updateProfile(1L, dto);

            assertThat(result.getUsername()).isEqualTo("newname");
            assertThat(result.getAvatar()).isEqualTo("https://example.com/avatar.png");
        }

        @Test
        @DisplayName("部分更新：仅更新用户名")
        void shouldPartialUpdateUsernameOnly() {
            SysUser updated = buildUser(1L, "newname");
            when(authService.updateUserProfile(1L, "newname", null)).thenReturn(updated);

            UpdateProfileDTO dto = new UpdateProfileDTO();
            dto.setUsername("newname");

            ProfileVO result = userService.updateProfile(1L, dto);

            assertThat(result.getUsername()).isEqualTo("newname");
            assertThat(result.getAvatar()).isEqualTo("https://avatar.example.com/old.png");
        }

        @Test
        @DisplayName("用户不存在时更新抛出 BizException")
        void shouldThrowBizExceptionWhenUserNotFound() {
            when(authService.updateUserProfile(999L, null, null)).thenThrow(new BizException(ErrorCode.USER_NOT_FOUND));

            assertThatThrownBy(() -> userService.updateProfile(999L, new UpdateProfileDTO()))
                    .isInstanceOf(BizException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ======================== 收货地址测试 ========================

    @Nested
    @DisplayName("创建收货地址")
    class CreateAddressTests {

        @Test
        @DisplayName("正常创建非默认地址")
        void shouldCreateNonDefaultAddress() {
            AddressDTO dto = buildAddressDTO("张三", "13800138000", 0);
            when(addressMapper.insert(any(UdAddress.class))).thenAnswer(inv -> {
                UdAddress a = inv.getArgument(0);
                a.setId(1L);
                return 1;
            });

            AddressVO result = userService.createAddress(1L, dto);

            assertThat(result.getReceiverName()).isEqualTo("张三");
            assertThat(result.getIsDefault()).isEqualTo(0);
            // 非默认地址不应清除已有默认地址
            verify(addressMapper, never()).clearDefault(anyLong());
        }

        @Test
        @DisplayName("创建默认地址时先清除旧默认地址")
        void shouldClearDefaultBeforeCreatingNewDefault() {
            AddressDTO dto = buildAddressDTO("李四", "13900139000", 1);
            when(addressMapper.insert(any(UdAddress.class))).thenAnswer(inv -> {
                UdAddress a = inv.getArgument(0);
                a.setId(2L);
                return 1;
            });

            AddressVO result = userService.createAddress(1L, dto);

            assertThat(result.getIsDefault()).isEqualTo(1);
            // 默认地址创建时先清除了旧默认地址
            verify(addressMapper).clearDefault(1L);
        }
    }

    @Nested
    @DisplayName("查询收货地址列表")
    class ListAddressesTests {

        @Test
        @DisplayName("正常查询地址列表")
        void shouldReturnAddressList() {
            UdAddress addr1 = buildUdAddress(1L, 1L, "张三", "13800138000", 1, LocalDateTime.now(), LocalDateTime.now());
            UdAddress addr2 = buildUdAddress(2L, 1L, "李四", "13900139000", 0, LocalDateTime.now(), LocalDateTime.now());
            when(addressMapper.selectByUserId(1L)).thenReturn(Arrays.asList(addr1, addr2));

            List<AddressVO> list = userService.listAddresses(1L);

            assertThat(list).hasSize(2);
            assertThat(list.get(0).getReceiverName()).isEqualTo("张三");
            assertThat(list.get(1).getReceiverName()).isEqualTo("李四");
        }

        @Test
        @DisplayName("用户无地址时返回空列表")
        void shouldReturnEmptyListWhenNoAddresses() {
            when(addressMapper.selectByUserId(1L)).thenReturn(Collections.emptyList());

            List<AddressVO> list = userService.listAddresses(1L);

            assertThat(list).isEmpty();
        }
    }

    @Nested
    @DisplayName("删除收货地址")
    class DeleteAddressTests {

        @Test
        @DisplayName("正常删除自己的地址")
        void shouldDeleteOwnAddress() {
            UdAddress address = buildUdAddress(10L, 1L, "张三", "13800138000", 0, null, null);
            when(addressMapper.selectById(10L)).thenReturn(address);

            userService.deleteAddress(1L, 10L);

            verify(addressMapper).deleteById(10L);
        }

        @Test
        @DisplayName("删除不存在的地址时抛出 BizException")
        void shouldThrowExceptionWhenAddressNotFound() {
            when(addressMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.deleteAddress(1L, 999L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("收货地址不存在");
        }

        @Test
        @DisplayName("无权删除他人的地址时抛出 BizException")
        void shouldThrowExceptionWhenDeletingOthersAddress() {
            UdAddress address = buildUdAddress(10L, 2L, "他人", "13800000000", 0, null, null);
            when(addressMapper.selectById(10L)).thenReturn(address);

            assertThatThrownBy(() -> userService.deleteAddress(1L, 10L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无权操作");
            verify(addressMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("设置默认地址")
    class SetDefaultAddressTests {

        @Test
        @DisplayName("正常设置默认地址")
        void shouldSetDefaultAddressSuccessfully() {
            UdAddress address = buildUdAddress(10L, 1L, "张三", "13800138000", 0, null, null);
            when(addressMapper.selectById(10L)).thenReturn(address);

            userService.setDefaultAddress(1L, 10L);

            // 先清除旧的默认地址，再设置新的默认地址
            verify(addressMapper).clearDefault(1L);
            verify(addressMapper).setDefault(10L);
        }

        @Test
        @DisplayName("设置他人地址为默认时抛出 BizException")
        void shouldThrowExceptionWhenSettingOthersDefault() {
            UdAddress address = buildUdAddress(10L, 2L, "他人", "13800000000", 0, null, null);
            when(addressMapper.selectById(10L)).thenReturn(address);

            assertThatThrownBy(() -> userService.setDefaultAddress(1L, 10L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("无权操作");
            verify(addressMapper, never()).clearDefault(anyLong());
            verify(addressMapper, never()).setDefault(anyLong());
        }

        @Test
        @DisplayName("设置不存在的地址为默认时抛出 BizException")
        void shouldThrowExceptionWhenAddressNotFound() {
            when(addressMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.setDefaultAddress(1L, 999L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("收货地址不存在");
        }
    }

    // ======================== 辅助方法 ========================

    private SysUser buildUser(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPhone("13800138000");
        user.setEmail("test@example.com");
        user.setAvatar("https://avatar.example.com/old.png");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        return user;
    }

    private AddressDTO buildAddressDTO(String name, String phone, Integer isDefault) {
        return AddressDTO.builder()
                .receiverName(name)
                .phone(phone)
                .province("广东省")
                .city("深圳市")
                .district("南山区")
                .detailAddress("科技园路1号")
                .isDefault(isDefault)
                .build();
    }

    private UdAddress buildUdAddress(Long id, Long userId, String name, String phone,
                                      Integer isDefault, LocalDateTime createdAt, LocalDateTime updatedAt) {
        UdAddress address = new UdAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setReceiverName(name);
        address.setPhone(phone);
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetailAddress("科技园路1号");
        address.setIsDefault(isDefault);
        address.setCreatedAt(createdAt);
        address.setUpdatedAt(updatedAt);
        return address;
    }
}
