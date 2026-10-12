package com.ecommerce.framework.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** 统一 Redis JSON 缓存访问，所有业务 key 由业务模块按约定传入。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {
    public static final String NULL_VALUE = "__NULL__";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException ex) {
            log.warn("Redis read failed, key={}: {}", key, ex.getMessage());
            return null;
        }
    }

    public <T> T getJson(String key, Class<T> type) {
        return getJson(key, objectMapper.getTypeFactory().constructType(type));
    }

    public <T> List<T> getListJson(String key, Class<T> elementType) {
        return getJson(key, objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public <T> T getJson(String key, JavaType type) {
        String value = get(key);
        if (value == null || NULL_VALUE.equals(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            log.warn("Redis JSON decode failed, key={}: {}", key, ex.getMessage());
            delete(key);
            return null;
        }
    }

    public boolean isNullValue(String key) {
        return NULL_VALUE.equals(get(key));
    }

    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (DataAccessException ex) {
            return false;
        }
    }

    public boolean setJson(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            return true;
        } catch (Exception ex) {
            log.warn("Redis write failed, key={}: {}", key, ex.getMessage());
            return false;
        }
    }

    public boolean set(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            return true;
        } catch (DataAccessException ex) {
            log.warn("Redis write failed, key={}: {}", key, ex.getMessage());
            return false;
        }
    }

    public boolean setNull(String key, Duration ttl) {
        return set(key, NULL_VALUE, ttl);
    }

    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (DataAccessException ex) {
            log.warn("Redis increment failed, key={}: {}", key, ex.getMessage());
            return null;
        }
    }

    public <T> T execute(RedisScript<T> script, java.util.List<String> keys, String... args) {
        try {
            return redisTemplate.execute(script, keys, (Object[]) args);
        } catch (DataAccessException ex) {
            log.warn("Redis script failed: {}", ex.getMessage());
            return null;
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException ex) {
            log.warn("Redis delete failed, key={}: {}", key, ex.getMessage());
        }
    }

    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (DataAccessException ex) {
            log.warn("Redis pattern delete failed, pattern={}: {}", pattern, ex.getMessage());
        }
    }
}
