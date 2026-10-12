package com.ecommerce.agent.ticket;

import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Distributed active-run quota backed by Redis sorted sets.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketRunQuotaStore {

    private static final String USER_KEY_PREFIX = "ecommerce:ticket:run:active:user:";
    private static final String RUN_KEY_PREFIX = "ecommerce:ticket:run:active:run:";

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>(
            """
            local now = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            local expiresAt = now + ttl * 1000
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZCARD', KEYS[1]) >= limit then
                return 0
            end
            redis.call('ZADD', KEYS[1], expiresAt, ARGV[4])
            redis.call('EXPIRE', KEYS[1], ttl + 300)
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> BIND_SCRIPT = new DefaultRedisScript<>(
            """
            local now = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local expiresAt = now + ttl * 1000
            local score = redis.call('ZSCORE', KEYS[1], ARGV[3])
            if not score or tonumber(score) <= now then
                return 0
            end
            redis.call('ZREM', KEYS[1], ARGV[3])
            redis.call('ZADD', KEYS[1], expiresAt, ARGV[4])
            redis.call('EXPIRE', KEYS[1], ttl + 300)
            redis.call('SET', KEYS[2], ARGV[5], 'EX', ttl + 300)
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> RELEASE_RUN_SCRIPT = new DefaultRedisScript<>(
            """
            local userId = redis.call('GET', KEYS[1])
            if not userId then
                return 0
            end
            local userKey = ARGV[2] .. userId
            redis.call('ZREM', userKey, ARGV[1])
            redis.call('DEL', KEYS[1])
            return 1
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public String reserve(Long userId, int maxActiveRuns, long ttlSeconds) {
        String reservationId = UUID.randomUUID().toString();
        String userKey = userKey(userId);
        long now = System.currentTimeMillis();
        try {
            Long result = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(userKey),
                    String.valueOf(now),
                    String.valueOf(Math.max(1, maxActiveRuns)),
                    String.valueOf(Math.max(60L, ttlSeconds)),
                    reservationId);
            if (result != 1L) {
                throw new BizException(
                        ErrorCode.TOO_MANY_REQUESTS,
                        "Too many active support agent runs");
            }
            return reservationId;
        } catch (BizException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.warn("Ticket run quota reserve failed: {}", ex.getMessage());
            throw new BizException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    "Support agent capacity guard unavailable");
        }
    }

    public boolean bindRun(Long userId, String reservationId, Long runId, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long ttl = Math.max(60L, ttlSeconds);
        try {
            Long result = redisTemplate.execute(
                    BIND_SCRIPT,
                    List.of(userKey(userId), runKey(runId)),
                    String.valueOf(now),
                    String.valueOf(ttl),
                    reservationId,
                    String.valueOf(runId),
                    String.valueOf(userId));
            return result == 1L;
        } catch (DataAccessException ex) {
            log.warn("Ticket run quota bind failed, runId={}: {}", runId, ex.getMessage());
            return false;
        }
    }

    public void releaseReservation(Long userId, String reservationId) {
        if (reservationId == null) {
            return;
        }
        try {
            redisTemplate.opsForZSet().remove(userKey(userId), reservationId);
        } catch (DataAccessException ex) {
            log.warn("Ticket run quota reservation release failed: {}", ex.getMessage());
        }
    }

    public void releaseRun(Long runId) {
        if (runId == null) {
            return;
        }
        try {
            redisTemplate.execute(
                    RELEASE_RUN_SCRIPT,
                    List.of(runKey(runId)),
                    String.valueOf(runId),
                    USER_KEY_PREFIX);
        } catch (DataAccessException ex) {
            log.warn("Ticket run quota release failed, runId={}: {}", runId, ex.getMessage());
        }
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + (userId == null ? "anonymous" : userId);
    }

    private String runKey(Long runId) {
        return RUN_KEY_PREFIX + runId;
    }
}
