package com.playground.distributed.case1;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis Sorted Set(ZSET) 기반 분산 처리율 제한(Rate Limiter) 서비스.
 * 슬라이딩 윈도우 알고리즘을 Lua 스크립트로 원자적으로 실행하여 요청 빈도를 제어합니다.
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    // 만료 타임스탬프 정리, 요청 카운트, 신규 타임스탬프 등록을 원자적으로 수행하는 Lua 스크립트
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

    @SuppressWarnings("unchecked")
    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<?> script = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, List.class);
        this.rateLimitScript = (RedisScript<List<Long>>) script;
    }

    /**
     * 클라이언트 요청이 슬라이딩 윈도우 허용량 이내인지 검사합니다.
     *
     * @param clientId 클라이언트 식별자
     * @param limit 윈도우 시간 내 허용 최대 요청 수
     * @param windowSeconds 윈도우 크기 (초 단위)
     * @return 허용 여부 및 잔여 횟수를 담은 RateLimitResult
     */
    public RateLimitResult checkLimit(String clientId, int limit, int windowSeconds) {
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

        if (result == null || result.size() < 2) {
            return RateLimitResult.BLOCKED;
        }

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);

        return RateLimitResult.of(allowed, remaining);
    }
}
