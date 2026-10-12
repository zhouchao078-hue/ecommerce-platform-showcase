package com.ecommerce.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.auth.model.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper 接口
 * 提供用户数据访问操作
 *
 * @author ecommerce
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体，未找到返回 null
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(String username);

    /**
     * 根据手机号码查询用户
     *
     * @param phone 手机号码
     * @return 用户实体，未找到返回 null
     */
    @Select("SELECT * FROM sys_user WHERE phone = #{phone}")
    SysUser selectByPhone(String phone);


}
