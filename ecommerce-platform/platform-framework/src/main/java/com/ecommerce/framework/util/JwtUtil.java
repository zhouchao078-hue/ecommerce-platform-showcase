package com.ecommerce.framework.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类，用于生成、解析和验证 JWT 令牌。
 * <p>
 * 使用 HMAC-SHA256 签名算法，密钥以 Base64 格式存储在配置文件中。
 * Token 中包含用户 ID、用户名、角色列表、签发时间和过期时间等信息。
 * </p>
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * JWT 签名密钥，Base64 编码
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * 访问令牌过期时间，默认 30 分钟（单位：毫秒）
     */
    @Value("${jwt.access-token-expiration:1800000}")
    private long accessTokenExpiration;

    /**
     * 刷新令牌过期时间，默认 7 天（单位：毫秒）
     */
    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    /**
     * 解析后的 HMAC-SHA256 密钥
     */
    private SecretKey secretKey;

    /**
     * Bean 初始化后，将 Base64 编码的密钥字符串解析为 SecretKey
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成访问令牌（Access Token）。
     * <p>
     * 令牌中存储用户 ID（作为 subject）、用户名、角色列表（逗号分隔）、签发时间和过期时间。
     * </p>
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param roles    角色列表
     * @return JWT 访问令牌字符串
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpiration);

        String rolesStr = roles != null && !roles.isEmpty()
                ? String.join(",", roles)
                : "";

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", rolesStr)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 生成刷新令牌（Refresh Token）。
     * <p>
     * 刷新令牌中仅存储用户 ID 和用户名，不包含角色信息，有效期较长（默认 7 天）。
     * </p>
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return JWT 刷新令牌字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 验证 JWT 令牌是否有效。
     * <p>
     * 解析令牌并对签名和过期时间进行校验，任何异常都会返回 false。
     * </p>
     *
     * @param token JWT 令牌字符串
     * @return 令牌有效返回 true，否则返回 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 令牌验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 JWT 令牌中提取用户 ID。
     *
     * @param token JWT 令牌字符串
     * @return 用户 ID，解析失败返回 null
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String sub = claims.getSubject();
            return Long.valueOf(sub);
        } catch (Exception e) {
            log.warn("从 JWT 令牌中提取用户 ID 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 JWT 令牌中提取用户名。
     *
     * @param token JWT 令牌字符串
     * @return 用户名，解析失败返回 null
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.get("username", String.class);
        } catch (Exception e) {
            log.warn("从 JWT 令牌中提取用户名失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 JWT 令牌中提取角色列表。
     * <p>
     * 角色在令牌中以逗号分隔的字符串形式存储，提取后拆分为列表返回。
     * </p>
     *
     * @param token JWT 令牌字符串
     * @return 角色字符串列表，解析失败或无角色时返回空列表
     */
    public List<String> getRolesFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String rolesStr = claims.get("roles", String.class);
            if (rolesStr == null || rolesStr.isBlank()) {
                return Collections.emptyList();
            }
            return Arrays.asList(rolesStr.split(","));
        } catch (Exception e) {
            log.warn("从 JWT 令牌中提取角色列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 JWT 令牌并返回 Claims 对象。
     *
     * @param token JWT 令牌字符串
     * @return 解析后的 Claims 对象
     * @throws JwtException 令牌解析或验证失败时抛出
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
