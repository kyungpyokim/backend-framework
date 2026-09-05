from collections.abc import AsyncGenerator
import redis.asyncio as aioredis
from redis.asyncio import Redis

from app.config import settings

# 애플리케이션 전역에서 재사용하는 비동기 Redis 클라이언트 인스턴스 (커넥션 풀 관리)
_redis_client: Redis | None = None


async def init_redis_pool() -> Redis:
    """
    애플리케이션 시작 시(Lifespan startup) 비동기 Redis 커넥션 풀을 초기화합니다.
    UTF-8 디코딩 옵션을 활성화하여 응답을 문자열로 자동 파싱합니다.
    """
    global _redis_client
    if _redis_client is None:
        _redis_client = aioredis.from_url(
            settings.redis_url,
            encoding="utf-8",
            decode_responses=True,
        )
    return _redis_client


async def close_redis_pool() -> None:
    """
    애플리케이션 종료 시(Lifespan shutdown) Redis 커넥션 풀을 정상적으로 닫습니다.
    """
    global _redis_client
    if _redis_client is not None:
        await _redis_client.aclose()
        _redis_client = None


async def get_redis() -> AsyncGenerator[Redis, None]:
    """
    FastAPI의 라우터 의존성 주입(Depends)에서 사용하는 Redis 클라이언트 제너레이터입니다.
    요청 처리 시 활성화된 커넥션 풀의 인스턴스를 주입합니다.
    """
    global _redis_client
    if _redis_client is None:
        await init_redis_pool()
    yield _redis_client  # type: ignore
