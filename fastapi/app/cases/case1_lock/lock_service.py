import asyncio
import uuid
from redis.asyncio import Redis

# Lua script to safely release lock only if the token matches (prevents releasing expired locks held by others)
RELEASE_LOCK_LUA = """
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
"""


class DistributedLock:
    def __init__(self, redis: Redis, resource: str, ttl_ms: int = 5000, retry_delay_ms: int = 50, max_retries: int = 20):
        self.redis = redis
        self.resource = resource
        self.lock_key = f"lock:{resource}"
        self.ttl_ms = ttl_ms
        self.retry_delay_ms = retry_delay_ms
        self.max_retries = max_retries
        self.token: str | None = None

    async def acquire(self) -> bool:
        token = str(uuid.uuid4())
        for _ in range(self.max_retries):
            # SET lock:resource token NX PX ttl_ms
            acquired = await self.redis.set(self.lock_key, token, px=self.ttl_ms, nx=True)
            if acquired:
                self.token = token
                return True
            await asyncio.sleep(self.retry_delay_ms / 1000.0)
        return False

    async def release(self) -> bool:
        if not self.token:
            return False
        result = await self.redis.eval(RELEASE_LOCK_LUA, 1, self.lock_key, self.token)
        return bool(result)

    async def __aenter__(self):
        acquired = await self.acquire()
        if not acquired:
            raise TimeoutError(f"Could not acquire lock for resource '{self.resource}'")
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.release()


async def init_inventory(redis: Redis, item_id: str, stock: int) -> None:
    await redis.set(f"stock:{item_id}", stock)


async def get_inventory(redis: Redis, item_id: str) -> int:
    val = await redis.get(f"stock:{item_id}")
    return int(val) if val is not None else 0


async def purchase_with_lock(redis: Redis, item_id: str, quantity: int = 1) -> dict:
    async with DistributedLock(redis, f"inventory:{item_id}"):
        current_stock = await get_inventory(redis, item_id)
        if current_stock < quantity:
            return {"success": False, "message": "Out of stock", "remaining": current_stock}
        # Artificial slight delay to simulate business validation / DB latency
        await asyncio.sleep(0.01)
        new_stock = current_stock - quantity
        await redis.set(f"stock:{item_id}", new_stock)
        return {"success": True, "message": "Purchase successful", "remaining": new_stock}


async def purchase_without_lock(redis: Redis, item_id: str, quantity: int = 1) -> dict:
    # Intentionally vulnerable to Race Condition for learning demonstration
    current_stock = await get_inventory(redis, item_id)
    if current_stock < quantity:
        return {"success": False, "message": "Out of stock", "remaining": current_stock}
    # During this delay, concurrent requests read the same stale current_stock!
    await asyncio.sleep(0.01)
    new_stock = current_stock - quantity
    await redis.set(f"stock:{item_id}", new_stock)
    return {"success": True, "message": "Purchase successful", "remaining": new_stock}
