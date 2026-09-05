import asyncio
import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_inventory_init_and_fetch(client: AsyncClient):
    # 1. Initialize stock
    init_res = await client.post("/case1/inventory/init", json={"item_id": "item-100", "stock": 50})
    assert init_res.status_code == 200
    assert init_res.json()["stock"] == 50

    # 2. Fetch stock
    fetch_res = await client.get("/case1/inventory/item-100")
    assert fetch_res.status_code == 200
    assert fetch_res.json()["stock"] == 50


@pytest.mark.asyncio
async def test_purchase_safe_concurrency(client: AsyncClient):
    # Initial stock: 10
    await client.post("/case1/inventory/init", json={"item_id": "item-concert-ticket", "stock": 10})

    # Concurrently send 10 safe purchase requests
    async def make_purchase():
        return await client.post(
            "/case1/purchase/safe",
            json={"item_id": "item-concert-ticket", "quantity": 1},
        )

    tasks = [make_purchase() for _ in range(10)]
    responses = await asyncio.gather(*tasks)

    # All 10 purchases should succeed
    for resp in responses:
        assert resp.status_code == 200
        assert resp.json()["success"] is True

    # Final stock must be exactly 0
    fetch_res = await client.get("/case1/inventory/item-concert-ticket")
    assert fetch_res.json()["stock"] == 0

    # 11th purchase must fail (Out of stock)
    eleventh = await client.post(
        "/case1/purchase/safe",
        json={"item_id": "item-concert-ticket", "quantity": 1},
    )
    assert eleventh.status_code == 200
    assert eleventh.json()["success"] is False
    assert eleventh.json()["message"] == "Out of stock"


@pytest.mark.asyncio
async def test_sliding_window_rate_limiting(client: AsyncClient):
    client_id = "test-user-42"
    # First 5 requests within window should succeed
    for i in range(5):
        resp = await client.get(f"/case1/rate-limit?client_id={client_id}")
        assert resp.status_code == 200
        assert resp.json()["allowed"] is True

    # 6th request must be rate limited (429 Too Many Requests)
    rate_limited_resp = await client.get(f"/case1/rate-limit?client_id={client_id}")
    assert rate_limited_resp.status_code == 429
