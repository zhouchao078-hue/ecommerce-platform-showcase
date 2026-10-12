package com.ecommerce.agent.concurrency;

import com.ecommerce.agent.config.AgentExecutionProperties;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Distributed chat admission control.
 *
 * <p>The counters live in Redis so multiple application instances share the
 * same per-user and global limits. Counters expire automatically if a process
 * crashes before releasing its permit.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConcurrencyGuard {

    private static final String GLOBAL_KEY = "ecommerce:agent:concurrency:global";
    private static final String USER_KEY_PREFIX = "ecommerce:agent:concurrency:user:";

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            """
            local userCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            local globalCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if userCount >= tonumber(ARGV[1]) or globalCount >= tonumber(ARGV[2]) then
                return 0
            end
            redis.call('INCR', KEYS[1])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            redis.call('INCR', KEYS[2])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            local userCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            local globalCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if userCount > 0 then
                redis.call('DECR', KEYS[1])
            end
            if globalCount > 0 then
                redis.call('DECR', KEYS[2])
            end
            return 1
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AgentExecutionProperties properties;

    public void acquire(Long userId) {
        String userKey = USER_KEY_PREFIX + (userId == null ? "anonymous" : userId);
        long leaseTtlSeconds = Math.max(
                properties.getSessionTtlSeconds(),
                properties.getChatTimeoutSeconds() * 2L + 60L);
        try {
            Long acquired = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    List.of(userKey, GLOBAL_KEY),
                    String.valueOf(properties.getPerUserConcurrency()),
                    String.valueOf(properties.getGlobalConcurrency()),
                    String.valueOf(leaseTtlSeconds));
            if (acquired == null || acquired != 1L) {
                throw new BizException(
                        ErrorCode.TOO_MANY_REQUESTS,
                        "Agent concurrency limit reached");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.warn("Agent concurrency guard unavailable: {}", ex.getMessage());
            throw new BizException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    "Agent capacity guard unavailable");
        }
    }

    public void release(Long userId) {
        String userKey = USER_KEY_PREFIX + (userId == null ? "anonymous" : userId);
        try {
            redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(userKey, GLOBAL_KEY));
        } catch (DataAccessException ex) {
            log.warn("Agent concurrency release failed: {}", ex.getMessage());
        }
    }
}
