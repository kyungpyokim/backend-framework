import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_circuit_breaker_success_and_fallback(client: AsyncClient):
    # 1. Ensure external service is healthy
    await client.post("/case4/external/fault", json={"is_healthy": True})

    # 2. Call service: should succeed normally
    res = await client.get("/case4/call")
    assert res.status_code == 200
    assert res.json()["result"]["status"] == "SUCCESS"
    assert res.json()["circuit_state"] == "CLOSED"

    # 3. Inject fault: external service goes down
    await client.post("/case4/external/fault", json={"is_healthy": False})

    # 4. Trigger failures up to threshold (3 failures)
    for _ in range(3):
        fail_res = await client.get("/case4/call")
        assert fail_res.status_code == 200
        # Fallback handler is triggered
        assert fail_res.json()["result"]["status"] == "FALLBACK"
        assert fail_res.json()["result"]["fallback_used"] is True

    # 5. Check circuit status: state should now be OPEN
    status_res = await client.get("/case4/circuit-status")
    assert status_res.status_code == 200
    assert status_res.json()["state"] == "OPEN"

    # 6. Subsequent calls return Fallback immediately without calling downstream
    fast_fail_res = await client.get("/case4/call")
    assert fast_fail_res.status_code == 200
    assert fast_fail_res.json()["result"]["fallback_used"] is True

    # 7. Cleanup: Restore healthy state
    await client.post("/case4/external/fault", json={"is_healthy": True})
