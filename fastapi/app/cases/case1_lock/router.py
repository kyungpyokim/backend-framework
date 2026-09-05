from typing import Annotated
from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from redis.asyncio import Redis

from app.config import settings
from app.core.redis import get_redis
from app.cases.case1_lock.lock_service import (
    init_inventory,
    get_inventory,
    purchase_with_lock,
    purchase_without_lock,
)
from app.cases.case1_lock.rate_limiter import check_rate_limit

router = APIRouter(prefix="/case1", tags=["Case 1: Stateless Scale-Out & Lock"])
RedisDep = Annotated[Redis, Depends(get_redis)]


class InventoryInitRequest(BaseModel):
    """재고 초기화 요청 DTO"""
    item_id: str
    stock: int = Field(gt=0, description="초기화할 양수 재고 수량")


class PurchaseRequest(BaseModel):
    """구매 요청 DTO"""
    item_id: str
    quantity: int = Field(default=1, gt=0, description="구매할 수량")


class PurchaseResponse(BaseModel):
    """구매 처리 결과 DTO"""
    node_id: str
    success: bool
    message: str
    remaining_stock: int


@router.post("/inventory/init")
async def setup_inventory(payload: InventoryInitRequest, redis: RedisDep) -> dict:
    """상품 재고를 초기화하는 엔드포인트"""
    await init_inventory(redis, payload.item_id, payload.stock)
    return {
        "node_id": settings.node_id,
        "item_id": payload.item_id,
        "stock": payload.stock,
        "message": "Inventory initialized",
    }


@router.get("/inventory/{item_id}")
async def fetch_inventory(item_id: str, redis: RedisDep) -> dict:
    """현재 상품 재고를 조회하는 엔드포인트"""
    stock = await get_inventory(redis, item_id)
    return {
        "node_id": settings.node_id,
        "item_id": item_id,
        "stock": stock,
    }


@router.post("/purchase/safe", response_model=PurchaseResponse)
async def buy_safe(payload: PurchaseRequest, redis: RedisDep) -> PurchaseResponse:
    """
    [동시성 안전] Redis 분산 락 기반 구매 엔드포인트.
    - 다중 인스턴스 환경에서도 하나의 요청만 락을 선점하여 재고를 차감합니다.
    - 락 획득 실패 시 429 Too Many Requests 예외를 반환합니다.
    """
    try:
        res = await purchase_with_lock(redis, payload.item_id, payload.quantity)
        return PurchaseResponse(
            node_id=settings.node_id,
            success=res["success"],
            message=res["message"],
            remaining_stock=res["remaining"],
        )
    except TimeoutError:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Server busy, please retry in a moment (lock acquisition timeout)",
        )


@router.post("/purchase/unsafe", response_model=PurchaseResponse)
async def buy_unsafe(payload: PurchaseRequest, redis: RedisDep) -> PurchaseResponse:
    """
    [동시성 취약] 분산 락 미적용 구매 엔드포인트 (동시성 결함 학습/시연용).
    - 동시 다발적인 요청 인입 시 동일한 재고를 읽어 초과 판매(음수 재고 등)가 발생합니다.
    """
    res = await purchase_without_lock(redis, payload.item_id, payload.quantity)
    return PurchaseResponse(
        node_id=settings.node_id,
        success=res["success"],
        message=res["message"],
        remaining_stock=res["remaining"],
    )


@router.get("/rate-limit")
async def rate_limit_test(
    redis: RedisDep,
    client_id: Annotated[str, Query()] = "client-default",
) -> dict:
    """
    [처리율 제한] 슬라이딩 윈도우 알고리즘 테스트 엔드포인트.
    - 10초 윈도우 내 최대 5회까지만 요청을 허용하며 초과 시 429 에러를 반환합니다.
    """
    # 10초 윈도우 내 최대 5회 허용
    allowed, remaining = await check_rate_limit(redis, client_id, limit=5, window_seconds=10)
    if not allowed:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Rate limit exceeded. Remaining: {remaining}",
        )
    return {
        "node_id": settings.node_id,
        "client_id": client_id,
        "allowed": allowed,
        "remaining_requests": remaining,
    }
