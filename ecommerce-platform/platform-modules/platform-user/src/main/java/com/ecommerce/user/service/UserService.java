package com.ecommerce.user.service;

import com.ecommerce.api.user.dto.AddressDTO;
import com.ecommerce.api.user.dto.UpdateProfileDTO;
import com.ecommerce.api.user.vo.AddressVO;
import com.ecommerce.api.user.vo.ProfileVO;

import java.util.List;

/**
 * 用户服务接口
 * 提供用户资料管理与收货地址管理的核心业务操作
 *
 * @author ecommerce
 */
public interface UserService {

    /**
     * 获取用户个人资料
     *
     * @param userId 用户ID
     * @return 用户资料
     */
    ProfileVO getProfile(Long userId);

    /**
     * 更新用户个人资料
     *
     * @param userId 用户ID
     * @param dto    更新请求
     * @return 更新后的用户资料
     */
    ProfileVO updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 创建收货地址
     *
     * @param userId 用户ID
     * @param dto    地址请求
     * @return 创建的地址
     */
    AddressVO createAddress(Long userId, AddressDTO dto);

    /**
     * 查询用户所有收货地址
     *
     * @param userId 用户ID
     * @return 地址列表
     */
    List<AddressVO> listAddresses(Long userId);

    /**
     * 删除收货地址
     *
     * @param userId    用户ID
     * @param addressId 地址ID
     */
    void deleteAddress(Long userId, Long addressId);

    /**
     * 设置默认收货地址
     *
     * @param userId    用户ID
     * @param addressId 地址ID
     */
    void setDefaultAddress(Long userId, Long addressId);

}
