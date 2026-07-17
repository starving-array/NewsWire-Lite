package com.fnmp.article.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    public static final Duration ARTICLE_TTL = Duration.ofMinutes(5);
    public static final Duration LIST_TTL = Duration.ofMinutes(2);
    private static final Duration STALE_TTL = Duration.ofMinutes(30);

    private static final String ARTICLE_PREFIX = "article:";
    private static final String LIST_PREFIX = "list:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, type));
            }
        } catch (Exception e) {
            log.warn("Cache read failed for key={}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public <T> StaleResult<T> getWithStale(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                Long expireSeconds = redis.getExpire(key);
                Duration ttl = expireSeconds != null ? Duration.ofSeconds(expireSeconds) : null;
                boolean isStale = ttl != null && ttl.isNegative();
                return new StaleResult<>(Optional.of(objectMapper.readValue(json, type)), isStale);
            }
        } catch (Exception e) {
            log.warn("Cache read failed for key={}: {}", key, e.getMessage());
        }
        return new StaleResult<>(Optional.empty(), false);
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            long jitteredTtl = ttl.getSeconds() + ThreadLocalRandom.current().nextLong(0, 60);
            redis.opsForValue().set(key, json, Duration.ofSeconds(jitteredTtl));
        } catch (Exception e) {
            log.warn("Cache write failed for key={}: {}", key, e.getMessage());
        }
    }

    public void putWithStale(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            long jitteredTtl = ttl.getSeconds() + ThreadLocalRandom.current().nextLong(0, 60);
            redis.opsForValue().set(key, json, Duration.ofSeconds(jitteredTtl + STALE_TTL.getSeconds()));
        } catch (Exception e) {
            log.warn("Cache write failed for key={}: {}", key, e.getMessage());
        }
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Cache evict failed for key={}: {}", key, e.getMessage());
        }
    }

    public void evictByPrefix(String prefix) {
        try {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Cache evict by prefix failed for prefix={}: {}", prefix, e.getMessage());
        }
    }

    public String articleKey(UUID articleId) {
        return ARTICLE_PREFIX + articleId;
    }

    public String listKey(String params) {
        return LIST_PREFIX + params.hashCode();
    }

    public <T> T getOrCompute(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        return get(key, type).orElseGet(() -> {
            T value = loader.get();
            put(key, value, ttl);
            return value;
        });
    }

    public <T> T getOrComputeWithStale(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        var result = getWithStale(key, type);
        if (result.value().isPresent()) {
            if (result.isStale()) {
                asyncRefresh(key, loader, ttl);
            }
            return result.value().get();
        }
        T value = loader.get();
        putWithStale(key, value, ttl);
        return value;
    }

    @Async
    protected <T> void asyncRefresh(String key, Supplier<T> loader, Duration ttl) {
        try {
            T value = loader.get();
            putWithStale(key, value, ttl);
            log.debug("Async cache refresh completed: key={}", key);
        } catch (Exception e) {
            log.warn("Async cache refresh failed: key={}", key, e);
        }
    }

    public record StaleResult<T>(Optional<T> value, boolean isStale) {
    }
}