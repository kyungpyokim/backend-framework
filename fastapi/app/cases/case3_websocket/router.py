from typing import Annotated, Any
from fastapi import APIRouter, Depends, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
from redis.asyncio import Redis

from app.config import settings
from app.core.redis import get_redis
from app.cases.case3_websocket.pubsub_manager import manager

router = APIRouter(prefix="/case3", tags=["Case 3: Realtime Broadcast & WebSocket"])
RedisDep = Annotated[Redis, Depends(get_redis)]


class BroadcastMessage(BaseModel):
    room_id: str
    sender: str
    content: str


@router.websocket("/ws/chat/{room_id}")
async def websocket_chat_endpoint(websocket: WebSocket, room_id: str, redis: RedisDep):
    await manager.connect(room_id, websocket)
    # Announce connection
    await manager.publish(
        redis,
        room_id,
        {
            "type": "system",
            "message": f"New user connected to room '{room_id}' via node [{settings.node_id}]",
            "node_id": settings.node_id,
        },
    )

    try:
        while True:
            text = await websocket.receive_text()
            # Publish incoming text to Redis so all cluster instances receive it
            await manager.publish(
                redis,
                room_id,
                {
                    "type": "chat",
                    "content": text,
                    "from_node": settings.node_id,
                },
            )
    except WebSocketDisconnect:
        manager.disconnect(room_id, websocket)
        await manager.publish(
            redis,
            room_id,
            {
                "type": "system",
                "message": f"User disconnected from room '{room_id}' on node [{settings.node_id}]",
                "node_id": settings.node_id,
            },
        )


@router.post("/broadcast")
async def broadcast_via_http(payload: BroadcastMessage, redis: RedisDep) -> dict:
    """Trigger a broadcast message from an external HTTP call."""
    msg = {
        "type": "broadcast",
        "sender": payload.sender,
        "content": payload.content,
        "origin_node": settings.node_id,
    }
    await manager.publish(redis, payload.room_id, msg)
    return {
        "status": "published",
        "room_id": payload.room_id,
        "origin_node": settings.node_id,
    }
