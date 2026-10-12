package com.ecommerce.framework.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义 UserDetails 实现类。
 * <p>
 * 实现 Spring Security 的 UserDetails 接口，用于封装当前登录用户的身份信息。
 * 包含用户 ID、用户名、密码、角色权限和启用状态等核心字段。
 * 角色信息以字符串列表形式传入，在 getAuthorities() 中转换为 GrantedAuthority 集合。
 * </p>
 */
@Getter
public class CustomUserDetails implements UserDetails {

    /**
     * 用户 ID
     */
    private final Long userId;

    /**
     * 用户名
     */
    private final String username;

    /**
     * 用户密码（加密后）
     */
    private final String password;

    /**
     * 用户角色/权限列表
     */
    private final List<GrantedAuthority> authorities;

    /**
     * 用户是否启用
     */
    private final boolean enabled;

    /**
     * 构造函数，根据用户基本信息构建 UserDetails 对象。
     * <p>
     * 角色字符串列表中的每个角色名都会被转换为 SimpleGrantedAuthority，
     * 默认添加 "ROLE_" 前缀如果角色名尚未包含。
     * </p>
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param password 用户密码（加密后）
     * @param roles    角色字符串列表
     */
    public CustomUserDetails(Long userId, String username, String password, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.authorities = roles.stream()
                .map(role -> {
                    if (role.startsWith("ROLE_")) {
                        return new SimpleGrantedAuthority(role);
                    }
                    return new SimpleGrantedAuthority("ROLE_" + role);
                })
                .collect(Collectors.toList());
        this.enabled = true;
    }

    /**
     * 获取用户权限集合。
     * <p>
     * 将角色字符串列表映射为 SimpleGrantedAuthority 集合返回。
     * </p>
     *
     * @return 用户权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账户是否未过期。
     * <p>
     * 默认返回 true，表示账户永不过期。
     * </p>
     *
     * @return true 表示账户未过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否未锁定。
     * <p>
     * 默认返回 true，表示账户未被锁定。
     * </p>
     *
     * @return true 表示账户未锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证（密码）是否未过期。
     * <p>
     * 默认返回 true，表示凭证永不过期。
     * </p>
     *
     * @return true 表示凭证未过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账户是否启用。
     *
     * @return true 表示账户已启用
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
