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
    """HTTP 브로드캐스트 요청 DTO"""
    room_id: str
    sender: str
    content: str


@router.websocket("/ws/chat/{room_id}")
async def websocket_chat_endpoint(websocket: WebSocket, room_id: str, redis: RedisDep):
    """
    [실시간 분산 채팅 엔드포인트]
    - 특정 방(room_id)으로 클라이언트의 웹소켓 연결을 수립합니다.
    - 연결 접속/종료 및 채팅 메시지 수신 시 Redis 채널로 발행(publish)하여
      다른 백엔드 인스턴스에 접속한 사용자들에게도 실시간으로 메시지를 동기화합니다.
    """
    await manager.connect(room_id, websocket)
    # 신규 접속 알림을 클러스터 전체에 브로드캐스트
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
            # 수신한 채팅 메시지를 Redis 채널로 발행하여 전체 노드로 전파
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
        # 퇴장 알림을 클러스터 전체에 브로드캐스트
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
    """
    [HTTP 기반 외부 브로드캐스트]
    - 웹소켓 연결이 없는 외부 시스템/배치 작업 등에서 HTTP API 호출을 통해
      특정 웹소켓 방(room)의 모든 접속자에게 실시간 알림/공지를 전파합니다.
    """
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
