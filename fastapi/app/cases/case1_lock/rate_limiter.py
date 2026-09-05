import time
import uuid
from redis.asyncio import Redis

SLIDING_WINDOW_LUA = """
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local unique_member = ARGV[4]

-- Remove timestamps older than the window
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- Count requests in current window
local count = redis.call('ZCARD', key)

if count < limit then
    redis.call('ZADD', key, now, unique_member)
    redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
    return {1, limit - count - 1}  -- {allowed, remaining}
else
    return {0, 0}  -- {not allowed, remaining}
end
"""


async def check_rate_limit(
    redis: Redis,
    client_id: str,
    limit: int = 5,
    window_seconds: int = 10,
) -> tuple[bool, int]:
    key = f"ratelimit:{client_id}"
    now_ms = int(time.time() * 1000)
    window_ms = window_seconds * 1000
    unique_member = f"{now_ms}:{uuid.uuid4().hex[:8]}"

    result = await redis.eval(
        SLIDING_WINDOW_LUA,
        1,
        key,
        now_ms,
        window_ms,
        limit,
        unique_member,
    )
    allowed = bool(result[0])
    remaining = int(result[1])
    return allowed, remaining
