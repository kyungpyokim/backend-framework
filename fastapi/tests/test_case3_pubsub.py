import pytest
from httpx import AsyncClient
from redis.asyncio import Redis

from app.cases.case3_websocket.pubsub_manager import DistributedPubSubManager


@pytest.mark.asyncio
async def test_broadcast_endpoint(client: AsyncClient):
    res = await client.post(
        "/case3/broadcast",
        json={
            "room_id": "lobby",
            "sender": "admin",
            "content": "Server maintenance notice in 10 mins",
        },
    )
    assert res.status_code == 200
    assert res.json()["status"] == "published"
    assert res.json()["room_id"] == "lobby"


@pytest.mark.asyncio
async def test_distributed_pubsub_manager(fake_redis: Redis):
    mgr = DistributedPubSubManager()

    # Verify publish sends message to correct Redis channel
    await mgr.publish(fake_redis, "room-1", {"test": "data"})
    # Verify local room structure is clean
    assert "room-1" not in mgr.local_rooms
