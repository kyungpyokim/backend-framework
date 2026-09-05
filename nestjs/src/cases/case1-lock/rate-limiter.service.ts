import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { RedisService } from '../../core/redis/redis.service';

// Redis ZSET 기반 슬라이딩 윈도우 처리율 제한 Lua 스크립트.
// 현재 시간 이전의 만료된 타임스탬프를 제거하고 유효 요청 수를 원자적으로 계산합니다.
const SLIDING_WINDOW_LUA = `
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local unique_member = ARGV[4]

-- 윈도우 범위를 벗어난 오래된 타임스탬프 제거
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
-- 현재 유효 윈도우 내 요청 개수 조회
local count = redis.call('ZCARD', key)

if count < limit then
    redis.call('ZADD', key, now, unique_member)
    redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
    return {1, limit - count - 1}
else
    return {0, 0}
end
`;

/**
 * 분산 처리율 제한(Rate Limiter) 서비스.
 * 클라이언트별 요청 빈도를 Redis Sorted Set을 사용해 슬라이딩 윈도우 방식으로 제한합니다.
 */
@Injectable()
export class RateLimiterService {
  constructor(private readonly redisService: RedisService) {}

  /**
   * 지정한 클라이언트의 요청이 허용 한도 내에 있는지 검사합니다.
   *
   * @param clientId 클라이언트 식별자 (IP 또는 사용자 ID)
   * @param limit 허용 요청 수 (기본값: 5회)
   * @param windowSeconds 윈도우 시간 (기본값: 10초)
   */
  async checkLimit(clientId: string, limit = 5, windowSeconds = 10): Promise<{ allowed: boolean; remaining: number }> {
    const redis = this.redisService.getClient();
    const key = `ratelimit:${clientId}`;
    const nowMs = Date.now();
    const windowMs = windowSeconds * 1000;
    const uniqueMember = `${nowMs}:${randomUUID().slice(0, 8)}`;

    const result = (await redis.eval(
      SLIDING_WINDOW_LUA,
      1,
      key,
      nowMs.toString(),
      windowMs.toString(),
      limit.toString(),
      uniqueMember,
    )) as [number, number];

    return {
      allowed: result[0] === 1,
      remaining: result[1],
    };
  }
}
