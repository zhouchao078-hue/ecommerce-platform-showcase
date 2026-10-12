package com.ecommerce.auth.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色实体类
 * 对应数据库表 sys_role
 *
 * @author ecommerce
 */
@Data
@TableName("sys_role")
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色名称（中文显示名）
     */
    private String name;

    /**
     * 角色编码（英文标识，如 ROLE_USER、ROLE_ADMIN）
     */
    private String code;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
