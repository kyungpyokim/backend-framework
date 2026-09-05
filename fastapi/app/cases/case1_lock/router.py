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
    item_id: str
    stock: int = Field(gt=0)


class PurchaseRequest(BaseModel):
    item_id: str
    quantity: int = Field(default=1, gt=0)


class PurchaseResponse(BaseModel):
    node_id: str
    success: bool
    message: str
    remaining_stock: int


@router.post("/inventory/init")
async def setup_inventory(payload: InventoryInitRequest, redis: RedisDep) -> dict:
    await init_inventory(redis, payload.item_id, payload.stock)
    return {
        "node_id": settings.node_id,
        "item_id": payload.item_id,
        "stock": payload.stock,
        "message": "Inventory initialized",
    }


@router.get("/inventory/{item_id}")
async def fetch_inventory(item_id: str, redis: RedisDep) -> dict:
    stock = await get_inventory(redis, item_id)
    return {
        "node_id": settings.node_id,
        "item_id": item_id,
        "stock": stock,
    }


@router.post("/purchase/safe", response_model=PurchaseResponse)
async def buy_safe(payload: PurchaseRequest, redis: RedisDep) -> PurchaseResponse:
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
