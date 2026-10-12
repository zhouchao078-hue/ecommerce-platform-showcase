package com.ecommerce.user.mapper;

import com.ecommerce.user.model.UdAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UdAddressMapper 单元测试
 * 使用 H2 内存数据库验证数据访问操作
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("UdAddressMapper 测试")
class UdAddressMapperTest {

    @Autowired
    private UdAddressMapper addressMapper;

    @Test
    @DisplayName("插入收货地址")
    void testInsert() {
        UdAddress address = new UdAddress();
        address.setUserId(1L);
        address.setReceiverName("张三");
        address.setPhone("13800138000");
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetailAddress("科技园路1号");
        address.setIsDefault(1);

        int rows = addressMapper.insert(address);
        assertThat(rows).isEqualTo(1);
        assertThat(address.getId()).isNotNull();
    }

    @Test
    @DisplayName("根据用户ID查询地址列表，默认地址优先")
    void testSelectByUserId() {
        UdAddress addr1 = buildAddress(1L, "张三", "13800138000", false);
        UdAddress addr2 = buildAddress(1L, "李四", "13900139000", true);
        addressMapper.insert(addr1);
        addressMapper.insert(addr2);

        List<UdAddress> list = addressMapper.selectByUserId(1L);
        assertThat(list).hasSize(2);
        // 默认地址应排在第一位
        assertThat(list.get(0).getIsDefault()).isEqualTo(1);
        assertThat(list.get(0).getReceiverName()).isEqualTo("李四");
        assertThat(list.get(1).getIsDefault()).isEqualTo(0);
        assertThat(list.get(1).getReceiverName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("清除指定用户的所有默认地址")
    void testClearDefault() {
        UdAddress addr1 = buildAddress(1L, "张三", "13800138000", true);
        UdAddress addr2 = buildAddress(1L, "李四", "13900139000", true);
        addressMapper.insert(addr1);
        addressMapper.insert(addr2);

        addressMapper.clearDefault(1L);

        List<UdAddress> list = addressMapper.selectByUserId(1L);
        assertThat(list).hasSize(2);
        assertThat(list).allMatch(a -> a.getIsDefault() == 0);
    }

    @Test
    @DisplayName("设置指定地址为默认地址")
    void testSetDefault() {
        UdAddress addr = buildAddress(1L, "张三", "13800138000", false);
        addressMapper.insert(addr);

        addressMapper.setDefault(addr.getId());

        UdAddress updated = addressMapper.selectById(addr.getId());
        assertThat(updated.getIsDefault()).isEqualTo(1);
    }

    @Test
    @DisplayName("清除默认地址仅影响指定用户，不影响其他用户")
    void testClearDefaultOnlyAffectsTargetUser() {
        UdAddress addr1 = buildAddress(1L, "用户A", "13800000001", true);
        UdAddress addr2 = buildAddress(2L, "用户B", "13800000002", true);
        addressMapper.insert(addr1);
        addressMapper.insert(addr2);

        addressMapper.clearDefault(1L);

        List<UdAddress> user1Addrs = addressMapper.selectByUserId(1L);
        List<UdAddress> user2Addrs = addressMapper.selectByUserId(2L);
        assertThat(user1Addrs.get(0).getIsDefault()).isEqualTo(0);
        assertThat(user2Addrs.get(0).getIsDefault()).isEqualTo(1);
    }

    private UdAddress buildAddress(Long userId, String name, String phone, boolean isDefault) {
        UdAddress address = new UdAddress();
        address.setUserId(userId);
        address.setReceiverName(name);
        address.setPhone(phone);
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetailAddress("科技园路1号");
        address.setIsDefault(isDefault ? 1 : 0);
        return address;
    }
}
