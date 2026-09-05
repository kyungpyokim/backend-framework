package com.playground.distributed.case1;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings({"null", "unchecked"})
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    private static final String SLIDING_WINDOW_LUA =
            "local key = KEYS[1] "
                    + "local now = tonumber(ARGV[1]) "
                    + "local window = tonumber(ARGV[2]) "
                    + "local limit = tonumber(ARGV[3]) "
                    + "local unique_member = ARGV[4] "
                    + "redis.call('ZREMRANGEBYSCORE', key, 0, now - window) "
                    + "local count = redis.call('ZCARD', key) "
                    + "if count < limit then "
                    + "    redis.call('ZADD', key, now, unique_member) "
                    + "    redis.call('EXPIRE', key, math.ceil(window / 1000) + 1) "
                    + "    return {1, limit - count - 1} "
                    + "else "
                    + "    return {0, 0} "
                    + "end";

    private final RedisScript<List<Long>> rateLimitScript;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<?> script = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, List.class);
        this.rateLimitScript = (RedisScript<List<Long>>) script;
    }

    public Map<String, Object> checkLimit(String clientId, int limit, int windowSeconds) {
        String key = "ratelimit:" + clientId;
        long nowMs = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        String uniqueMember = nowMs + ":" + UUID.randomUUID().toString().substring(0, 8);

        List<Long> result =
                redisTemplate.execute(
                        rateLimitScript,
                        Collections.singletonList(key),
                        String.valueOf(nowMs),
                        String.valueOf(windowMs),
                        String.valueOf(limit),
                        uniqueMember);

        boolean allowed = result != null && result.get(0) == 1L;
        long remaining = result != null ? result.get(1) : 0L;

        return Map.of("allowed", allowed, "remaining", remaining);
    }
}
