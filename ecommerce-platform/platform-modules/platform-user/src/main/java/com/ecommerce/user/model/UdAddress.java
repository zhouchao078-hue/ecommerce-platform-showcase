package com.ecommerce.user.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户收货地址实体类
 * 对应数据库表 ud_address
 *
 * @author ecommerce
 */
@Data
@TableName("ud_address")
public class UdAddress implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 地址主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，关联 sys_user 表
     */
    private Long userId;

    /**
     * 收货人姓名
     */
    @TableField("receiver_name")
    private String receiverName;

    /**
     * 收货人手机号
     */
    private String phone;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 区/县
     */
    private String district;

    /**
     * 详细地址（街道、门牌号等）
     */
    @TableField("detail_address")
    private String detailAddress;

    /**
     * 是否默认地址：0-否，1-是
     * 该字段由业务逻辑手动设置，不使用自动填充策略
     */
    @TableField("is_default")
    private Integer isDefault;

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
}
