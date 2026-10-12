package com.ecommerce.user.service.impl;

import com.ecommerce.api.user.dto.AddressDTO;
import com.ecommerce.api.user.dto.UpdateProfileDTO;
import com.ecommerce.api.user.vo.AddressVO;
import com.ecommerce.api.user.vo.ProfileVO;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.user.mapper.UdAddressMapper;
import com.ecommerce.user.model.UdAddress;
import com.ecommerce.auth.model.SysUser;
import com.ecommerce.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * 负责用户资料管理与收货地址管理的核心业务逻辑
 *
 * @author ecommerce
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UdAddressMapper addressMapper;
    @Autowired  // 注入 AuthService
    private AuthService AuthService;

    // ======================== 用户资料 ========================

    @Override
    public ProfileVO getProfile(Long userId) {
        SysUser user = AuthService.getUserById(userId);
        return toProfileVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileVO updateProfile(Long userId, UpdateProfileDTO dto) {
        SysUser user = AuthService.updateUserProfile(userId, dto.getUsername(), dto.getAvatar());
        log.info("用户资料更新成功，userId={}", userId);
        return toProfileVO(user);
    }

    // ======================== 收货地址 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddressVO createAddress(Long userId, AddressDTO dto) {
        // 如果本次地址设为默认，先清除原有默认地址
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            addressMapper.clearDefault(userId);
        }
        UdAddress address = toUdAddress(userId, dto);
        addressMapper.insert(address);
        log.info("收货地址创建成功，addressId={}，userId={}", address.getId(), userId);
        return toAddressVO(address);
    }

    @Override
    public List<AddressVO> listAddresses(Long userId) {
        List<UdAddress> addresses = addressMapper.selectByUserId(userId);
        return addresses.stream()
                .map(this::toAddressVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long addressId) {
        UdAddress address = addressMapper.selectById(addressId);
        if (address == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "收货地址不存在");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该收货地址");
        }
        addressMapper.deleteById(addressId);
        log.info("收货地址删除成功，addressId={}，userId={}", addressId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultAddress(Long userId, Long addressId) {
        UdAddress address = addressMapper.selectById(addressId);
        if (address == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "收货地址不存在");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该收货地址");
        }
        addressMapper.clearDefault(userId);
        addressMapper.setDefault(addressId);
        log.info("默认地址设置成功，addressId={}，userId={}", addressId, userId);
    }

    // ======================== 实体与VO映射方法 ========================

    /**
     * 将 SysUser 实体映射为 ProfileVO
     */
    private ProfileVO toProfileVO(SysUser user) {
        return ProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * 将 AddressDTO 转换为 UdAddress 实体（用于新增）
     */
    private UdAddress toUdAddress(Long userId, AddressDTO dto) {
        UdAddress address = new UdAddress();
        address.setUserId(userId);
        address.setReceiverName(dto.getReceiverName());
        address.setPhone(dto.getPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetailAddress(dto.getDetailAddress());
        address.setIsDefault(dto.getIsDefault());
        return address;
    }

    /**
     * 将 UdAddress 实体映射为 AddressVO
     */
    private AddressVO toAddressVO(UdAddress address) {
        return AddressVO.builder()
                .id(address.getId())
                .userId(address.getUserId())
                .receiverName(address.getReceiverName())
                .phone(address.getPhone())
                .province(address.getProvince())
                .city(address.getCity())
                .district(address.getDistrict())
                .detailAddress(address.getDetailAddress())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}