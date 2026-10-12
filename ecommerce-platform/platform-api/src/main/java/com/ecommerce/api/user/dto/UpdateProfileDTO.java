package com.ecommerce.api.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求体
 */
@Data
public class UpdateProfileDTO {

    /**
     * 用户名，长度 1-32 个字符
     */
    @Size(min = 1, max = 32, message = "用户名长度必须在1到32个字符之间")
    private String username;

    /**
     * 头像URL
     */
    private String avatar;
}
