from collections.abc import AsyncGenerator
import redis.asyncio as aioredis
from redis.asyncio import Redis

from app.config import settings

_redis_client: Redis | None = None


async def init_redis_pool() -> Redis:
    global _redis_client
    if _redis_client is None:
        _redis_client = aioredis.from_url(
            settings.redis_url,
            encoding="utf-8",
            decode_responses=True,
        )
    return _redis_client


async def close_redis_pool() -> None:
    global _redis_client
    if _redis_client is not None:
        await _redis_client.aclose()
        _redis_client = None


async def get_redis() -> AsyncGenerator[Redis, None]:
    global _redis_client
    if _redis_client is None:
        await init_redis_pool()
    yield _redis_client  # type: ignore
