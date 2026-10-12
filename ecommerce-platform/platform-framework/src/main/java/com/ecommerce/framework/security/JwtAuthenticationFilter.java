package com.ecommerce.framework.security;

import com.ecommerce.framework.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器，拦截每个请求并验证 JWT 令牌。
 * <p>
 * 该过滤器继承自 OncePerRequestFilter，确保每个请求只被过滤一次。
 * 对于指定的白名单路径（如登录、注册、刷新令牌接口），直接放行不做验证。
 * 对于其他请求，从 Authorization 头中提取 Bearer Token，验证通过后将用户信息
 * 设置到 SecurityContextHolder 中。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 免登录白名单统一在 SecurityConfig 里维护，本类不再保留一份，避免两处不一致。

    private final JwtUtil jwtUtil;

    /**
     * 过滤器核心逻辑：对每个请求提取 JWT Token 并验证。
     * <p>
     * 白名单路径直接放行；非白名单路径从 Authorization 头中提取 Bearer Token，
     * 校验通过后将用户信息封装为 Authentication 对象并设置到 SecurityContext 中。
     * Token 无效或缺失时直接放行，由后续的 Spring Security 配置决定是否拒绝访问。
     * </p>
     *
     * @param request     当前 HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getServletPath();

        // 公开接口也要尝试解析 token。
        // 像 /api/shops/{id} 这种「公开、但登录后要多返回一个 isFollowing」的接口，
        // 如果在这里提前 return，SecurityContext 永远是空的，
        // getCurrentUserIdOrNull() 会一直返回 null，关注状态就永远显示不对。
        // 是否放行由 SecurityConfig 的授权规则决定，本过滤器只负责「带了 token 就认出来」。
        String token = extractTokenFromRequest(request);

        if (token != null && jwtUtil.validateToken(token)) {
            // 从 Token 中提取用户信息
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            List<String> roles = jwtUtil.getRolesFromToken(token);

            // 将角色字符串转换为 GrantedAuthority 列表
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // 构建认证对象，用户名为 userId 的字符串形式
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            String.valueOf(userId),
                            null,
                            authorities
                    );
            authentication.setDetails(username);

            // 将认证对象设置到 SecurityContext 中
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("用户认证成功: userId={}, username={}", userId, username);
        } else {
            // Token 缺失或无效，记录日志，清除安全上下文
            SecurityContextHolder.clearContext();
            if (token != null) {
                log.warn("JWT Token 无效: path={}", requestPath);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求中提取 JWT Token。
     * <p>
     * 从 Authorization 请求头中解析 Bearer Token，格式为 "Bearer {token}"。
     * </p>
     *
     * @param request HTTP 请求
     * @return 提取到的 JWT Token 字符串，未找到或格式不正确时返回 null
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
