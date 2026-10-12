package com.ecommerce.framework.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 安全配置类。
 * <p>
 * 配置基于 JWT 的无状态认证机制，禁用 CSRF 和 Session，
 * 定义白名单路径和认证要求，注册 JWT 认证过滤器。
 * 同时启用方法级别的权限控制 (@EnableMethodSecurity)。
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 配置 SecurityFilterChain 安全过滤器链。
     * <p>
     * 使用 Lambda DSL 风格配置：
     * <ul>
     *   <li>禁用 CSRF 保护（适用于无状态 API）</li>
     *   <li>设置 Session 为无状态模式</li>
     *   <li>配置白名单路径允许匿名访问</li>
     *   <li>其他所有请求需要认证</li>
     *   <li>将 JWT 认证过滤器添加到 UsernamePasswordAuthenticationFilter 之前</li>
     * </ul>
     * </p>
     *
     * @param http HttpSecurity 对象
     * @return 配置好的 SecurityFilterChain
     * @throws Exception 配置过程中可能抛出的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF 保护，适用于前后端分离的无状态 API
                .csrf(AbstractHttpConfigurer::disable)

                // 禁用 Session，使用无状态的 JWT 认证
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 配置请求授权规则
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(
                                DispatcherType.ASYNC,
                                DispatcherType.ERROR).permitAll()
                        // 白名单路径：登录/注册/刷新令牌等无需认证的接口
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/internal/**",
                                "/actuator/health",
                                "/actuator/prometheus",
                                "/error",
                                "/doc.html",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**"
                        ).permitAll()
                        .requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
                        // 公开浏览接口：商品列表/搜索/详情、分类列表（GET 请求）
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products/*",
                                "/api/categories/*",
                                "/api/shops/*",
                                "/api/shops/**",
                                "/api/seckill/products",
                                "/api/seckill/products/*"
                        ).permitAll()
                        // 其他所有请求需要认证（包括 /api/auth/me）
                        .anyRequest().authenticated()
                )

                // 未认证/令牌失效时返回 401（而不是默认 403），便于前端统一处理跳转登录
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
                        })
                )

                // 将 JWT 认证过滤器添加到 Spring Security 过滤器链中，
                // 位置在 UsernamePasswordAuthenticationFilter 之前
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 创建 BCrypt 密码编码器 Bean。
     * <p>
     * 使用 BCryptPasswordEncoder 对密码进行不可逆加密，
     * 用于用户注册和登录时的密码校验。
     * </p>
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 创建 AuthenticationManager Bean。
     * <p>
     * 从 AuthenticationConfiguration 中获取 AuthenticationManager 实例，
     * 用于在登录接口中手动进行认证。
     * </p>
     *
     * @param authConfig Spring Security 认证配置
     * @return AuthenticationManager 实例
     * @throws Exception 获取认证管理器过程中可能抛出的异常
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
