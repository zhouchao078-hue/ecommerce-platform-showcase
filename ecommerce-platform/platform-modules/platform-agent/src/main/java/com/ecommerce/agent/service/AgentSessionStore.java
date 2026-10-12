package com.ecommerce.agent.service;

import com.ecommerce.agent.config.AgentExecutionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed bounded session history shared by all application instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionStore {

    private static final String KEY_PREFIX = "ecommerce:agent:session:";

    private static final DefaultRedisScript<Long> APPEND_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('RPUSH', KEYS[1], ARGV[1])
            redis.call('LTRIM', KEYS[1], -tonumber(ARGV[2]), -1)
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AgentExecutionProperties properties;

    public void append(String sessionId, String role, String content) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String entry = "[" + role + "] " + (content == null ? "" : content);
        try {
            redisTemplate.execute(
                    APPEND_SCRIPT,
                    Collections.singletonList(key(sessionId)),
                    entry,
                    String.valueOf(Math.max(1, properties.getSessionHistoryLimit())),
                    String.valueOf(Math.max(60L, properties.getSessionTtlSeconds())));
        } catch (DataAccessException ex) {
            log.warn("Agent session append failed, sessionId={}: {}", sessionId, ex.getMessage());
        }
    }

    public List<String> get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        String key = key(sessionId);
        try {
            List<String> values = redisTemplate.opsForList().range(key, 0, -1);
            redisTemplate.expire(
                    key,
                    Duration.ofSeconds(Math.max(60L, properties.getSessionTtlSeconds())));
            return values == null ? List.of() : List.copyOf(values);
        } catch (DataAccessException ex) {
            log.warn("Agent session read failed, sessionId={}: {}", sessionId, ex.getMessage());
            return List.of();
        }
    }

    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(key(sessionId));
        } catch (DataAccessException ex) {
            log.warn("Agent session clear failed, sessionId={}: {}", sessionId, ex.getMessage());
        }
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId + ":history";
    }
}
