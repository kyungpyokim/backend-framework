import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { RedisService } from '../../core/redis/redis.service';

const SLIDING_WINDOW_LUA = `
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local unique_member = ARGV[4]

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)

if count < limit then
    redis.call('ZADD', key, now, unique_member)
    redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
    return {1, limit - count - 1}
else
    return {0, 0}
end
`;

@Injectable()
export class RateLimiterService {
  constructor(private readonly redisService: RedisService) {}

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
