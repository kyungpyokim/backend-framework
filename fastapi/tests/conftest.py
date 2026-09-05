from collections.abc import AsyncGenerator
import fakeredis.aioredis
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from redis.asyncio import Redis

from app.core.redis import get_redis
from app.main import create_app


@pytest_asyncio.fixture
async def fake_redis() -> AsyncGenerator[Redis, None]:
    # In-memory async fake Redis for unit and integration tests
    server = fakeredis.FakeServer()
    r = fakeredis.aioredis.FakeRedis(server=server, decode_responses=True)
    yield r  # type: ignore
    await r.flushall()
    await r.aclose()


@pytest_asyncio.fixture
async def client(fake_redis: Redis) -> AsyncGenerator[AsyncClient, None]:
    app = create_app()

    async def override_get_redis():
        yield fake_redis

    app.dependency_overrides[get_redis] = override_get_redis

    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as ac:
        yield ac
