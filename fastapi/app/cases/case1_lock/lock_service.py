import asyncio
import uuid
from redis.asyncio import Redis

# 락을 안전하게 해제하기 위한 Lua 스크립트.
# 락 키의 현재 값(토큰)이 내가 발급받은 토큰과 일치할 때만 원자적으로 삭제(del)합니다.
# 이유: 비즈니스 로직 실행이 오래 걸려 TTL이 만료된 후 다른 프로세스가 획득한 락을 오삭제하는 현상을 방지합니다.
RELEASE_LOCK_LUA = """
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
"""


class DistributedLock:
    """
    Redis 기반 분산 락 (Distributed Lock) 구현체.
    다중 인스턴스/프로세스 환경에서 특정 공유 자원(Critical Section)에 대한 상호 배제(Mutual Exclusion)를 보장합니다.
    """

    def __init__(self, redis: Redis, resource: str, ttl_ms: int = 5000, retry_delay_ms: int = 50, max_retries: int = 20):
        """
        :param redis: Redis 비동기 클라이언트
        :param resource: 락을 적용할 자원 식별자 (예: 'inventory:item-1')
        :param ttl_ms: 락 유지 시간(밀리초). 노드 장애 시 무한 데드락을 방지하기 위한 자동 만료 시간
        :param retry_delay_ms: 락 획득 실패 시 재시도 대기 간격(밀리초)
        :param max_retries: 락 획득 최대 재시도 횟수
        """
        self.redis = redis
        self.resource = resource
        self.lock_key = f"lock:{resource}"
        self.ttl_ms = ttl_ms
        self.retry_delay_ms = retry_delay_ms
        self.max_retries = max_retries
        self.token: str | None = None

    async def acquire(self) -> bool:
        """
        분산 락 획득을 시도합니다.
        - UUID v4로 고유 토큰을 생성합니다.
        - SET NX(Not Exists) PX(밀리초 TTL) 옵션으로 원자적 락 생성을 시도합니다.
        - 획득 실패 시 일정 대기 후 최대 max_retries 만큼 재시도합니다.
        """
        token = str(uuid.uuid4())
        for _ in range(self.max_retries):
            # SET lock:resource token NX PX ttl_ms (원자적 연산)
            acquired = await self.redis.set(self.lock_key, token, px=self.ttl_ms, nx=True)
            if acquired:
                self.token = token
                return True
            await asyncio.sleep(self.retry_delay_ms / 1000.0)
        return False

    async def release(self) -> bool:
        """
        획득했던 분산 락을 안전하게 해제합니다.
        내가 보유한 고유 토큰과 일치할 때만 원자적으로 키를 삭제합니다.
        """
        if not self.token:
            return False
        result = await self.redis.eval(RELEASE_LOCK_LUA, 1, self.lock_key, self.token)
        return bool(result)

    async def __aenter__(self):
        """async with 구문 진입 시 락 획득을 시도하며, 실패 시 TimeoutError 발생"""
        acquired = await self.acquire()
        if not acquired:
            raise TimeoutError(f"Could not acquire lock for resource '{self.resource}'")
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """async with 구문 종료 시 예외 발생 여부와 상관없이 락을 안전하게 해제"""
        await self.release()


async def init_inventory(redis: Redis, item_id: str, stock: int) -> None:
    """상품 재고를 지정한 수량으로 초기화합니다."""
    await redis.set(f"stock:{item_id}", stock)


async def get_inventory(redis: Redis, item_id: str) -> int:
    """상품의 현재 남은 재고 수량을 조회합니다."""
    val = await redis.get(f"stock:{item_id}")
    return int(val) if val is not None else 0


async def purchase_with_lock(redis: Redis, item_id: str, quantity: int = 1) -> dict:
    """
    [분산 락 적용] 동시성 안전 구매 로직.
    - DistributedLock을 획득한 후 재고 확인 및 차감을 수행합니다.
    - 여러 서버/스레드에서 동시 요청이 들어와도 정확히 한 번에 하나의 요청만 임계 영역에 진입합니다.
    """
    async with DistributedLock(redis, f"inventory:{item_id}"):
        current_stock = await get_inventory(redis, item_id)
        if current_stock < quantity:
            return {"success": False, "message": "Out of stock", "remaining": current_stock}
        # 비즈니스 검증/DB I/O 지연 시간을 가상으로 시뮬레이션
        await asyncio.sleep(0.01)
        new_stock = current_stock - quantity
        await redis.set(f"stock:{item_id}", new_stock)
        return {"success": True, "message": "Purchase successful", "remaining": new_stock}


async def purchase_without_lock(redis: Redis, item_id: str, quantity: int = 1) -> dict:
    """
    [분산 락 미적용] 경쟁 상태(Race Condition) 취약 구매 로직 (학습 및 비교 데모용).
    - 락 없이 재고를 조회한 뒤 가상 지연 동안 다른 요청들이 동일한 재고를 읽음으로써
      초과 판매(Overselling)가 발생하는 현상을 재현합니다.
    """
    # 동시성 이슈 재현을 위해 의도적으로 락을 배제함
    current_stock = await get_inventory(redis, item_id)
    if current_stock < quantity:
        return {"success": False, "message": "Out of stock", "remaining": current_stock}
    # 이 지연 시간 동안 동시에 인입된 다른 요청들이 동일한 이전 current_stock을 읽게 됨!
    await asyncio.sleep(0.01)
    new_stock = current_stock - quantity
    await redis.set(f"stock:{item_id}", new_stock)
    return {"success": True, "message": "Purchase successful", "remaining": new_stock}
