package com.ecommerce.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.auth.model.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper 接口
 * 提供用户角色关联的数据访问操作
 * insert 方法继承自 BaseMapper，可直接使用
 *
 * @author ecommerce
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
