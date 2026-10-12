package com.ecommerce.auth.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户实体类
 * 对应数据库表 sys_user
 *
 * @author ecommerce
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名，唯一
     */
    private String username;

    /**
     * 密码哈希值（BCrypt加密）
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 手机号码，唯一
     */
    private String phone;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 用户头像URL
     */
    private String avatar;

    /**
     * 用户状态：1=启用，0=禁用
     */
    private Integer status;

    /**
     * 创建时间，插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间，插入和更新时自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 登录次数，默认为0
     */
    @TableField("login_count")
    private Integer loginCount = 0;
}
