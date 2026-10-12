package com.ecommerce.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.auth.model.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色 Mapper 接口
 * 提供角色数据访问操作
 *
 * @author ecommerce
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 根据用户ID查询该用户拥有的所有角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Select("SELECT r.* FROM sys_role r INNER JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<SysRole> selectRolesByUserId(Long userId);

    /**
     * 根据角色编码查询角色
     *
     * @param code 角色编码（如 ROLE_USER）
     * @return 角色实体，未找到返回 null
     */
    @Select("SELECT * FROM sys_role WHERE code = #{code}")
    SysRole selectByCode(String code);
}
