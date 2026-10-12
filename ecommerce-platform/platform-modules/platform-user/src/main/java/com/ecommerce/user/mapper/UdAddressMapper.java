package com.ecommerce.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.user.model.UdAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户收货地址 Mapper 接口
 * 提供地址数据访问操作
 *
 * @author ecommerce
 */
@Mapper
public interface UdAddressMapper extends BaseMapper<UdAddress> {

    /**
     * 根据用户ID查询其所有收货地址
     * 默认地址排在最前，其余按创建时间倒序排列
     *
     * @param userId 用户ID
     * @return 地址列表
     */
    @Select("SELECT * FROM ud_address WHERE user_id = #{userId} ORDER BY is_default DESC, created_at DESC")
    List<UdAddress> selectByUserId(Long userId);

    /**
     * 清除指定用户的所有默认地址标记
     * 将该用户所有地址的 is_default 置为 0
     *
     * @param userId 用户ID
     */
    @Update("UPDATE ud_address SET is_default = 0 WHERE user_id = #{userId}")
    void clearDefault(Long userId);

    /**
     * 将指定地址设置为默认地址
     * 将指定ID的地址 is_default 置为 1
     *
     * @param id 地址ID
     */
    @Update("UPDATE ud_address SET is_default = 1 WHERE id = #{id}")
    void setDefault(Long id);
}
