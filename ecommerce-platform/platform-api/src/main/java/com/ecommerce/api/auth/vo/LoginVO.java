package com.ecommerce.api.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 过期时间（秒），默认 1800 秒（30 分钟）
     */
    @Builder.Default
    private Long expiresIn = 30 * 60L;


    /**
     * 登录次数
     */
    private Integer loginCount;
}
