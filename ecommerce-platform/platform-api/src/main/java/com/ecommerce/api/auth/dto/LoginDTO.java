package com.ecommerce.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户名或手机号 + 密码登录
 */
@Data
public class LoginDTO {

    /**
     * 用户名或手机号
     */
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
