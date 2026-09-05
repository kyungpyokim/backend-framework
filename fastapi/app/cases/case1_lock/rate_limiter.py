import time
import uuid
from redis.asyncio import Redis

# Redis Sorted Set(ZSET) 기반 슬라이딩 윈도우(Sliding Window) 처리율 제한기 Lua 스크립트.
# 원자적 실행 이유: 타임스탬프 정리, 개수 세기, 새 요청 추가가 원자적으로 수행되지 않으면
# 동시 요청 시 정확한 Rate Limit 경계 검사가 불가능해집니다.
SLIDING_WINDOW_LUA = """
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local unique_member = ARGV[4]

-- 1. 현재 시간 기준 윈도우 범위를 벗어난 오래된 요청 타임스탬프 제거
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 2. 현재 윈도우 내에 남아 있는 유효 요청 개수 카운트
local count = redis.call('ZCARD', key)

-- 3. 임계치(limit) 미만인 경우 요청 허용 및 현재 타임스탬프 기록
if count < limit then
    redis.call('ZADD', key, now, unique_member)
    -- 메모리 누수 방지를 위한 TTL(만료 시간) 자동 갱신
    redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)
    return {1, limit - count - 1}  -- {허용 여부(1=True), 남은 허용량}
else
    return {0, 0}  -- {허용 여부(0=False), 남은 허용량}
end
"""


async def check_rate_limit(
    redis: Redis,
    client_id: str,
    limit: int = 5,
    window_seconds: int = 10,
) -> tuple[bool, int]:
    """
    슬라이딩 윈도우 알고리즘을 사용하여 클라이언트의 요청이 허용 한도 내에 있는지 검사합니다.

    :param redis: Redis 비동기 클라이언트
    :param client_id: 클라이언트 식별자 (IP 주소 또는 사용자 ID)
    :param limit: 윈도우 시간 동안 허용되는 최대 요청 횟수 (기본값: 5회)
    :param window_seconds: 윈도우 크기(초 단위, 기본값: 10초)
    :return: (허용 여부 bool, 윈도우 내 남은 요청 허용 횟수 int)
    """
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
