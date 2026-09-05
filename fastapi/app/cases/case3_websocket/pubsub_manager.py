import asyncio
import json
from collections import defaultdict
from typing import Any
from fastapi import WebSocket
from redis.asyncio import Redis

# Redis Pub/Sub 채널 네임스페이스 접두사
CHANNEL_PREFIX = "ws:room:"


class DistributedPubSubManager:
    """
    Redis Pub/Sub 기반의 분산 웹소켓 매니저.
    - 다중 서버(수평 확장) 환경에서는 특정 클라이언트가 어느 서버에 연결될지 알 수 없습니다.
    - 따라서 각 서버는 자신의 로컬 웹소켓 연결(local_rooms)만 관리하고,
      메시지 전송 시 Redis 채널로 발행(PUBLISH)하여 모든 서버 인스턴스가 수신하게 합니다.
    - 수신한 각 서버는 자신의 로컬 연결 중 해당 방(room)에 속한 세션에 메시지를 브로드캐스트합니다.
    """

    def __init__(self):
        # room_id -> 현재 이 특정 서버 인스턴스에 연결된 활성 WebSocket 세션 집합
        self.local_rooms: dict[str, set[WebSocket]] = defaultdict(set)
        self._listener_task: asyncio.Task | None = None

    async def connect(self, room_id: str, websocket: WebSocket) -> None:
        """클라이언트의 웹소켓 연결을 승인하고 로컬 방 세션 집합에 등록합니다."""
        await websocket.accept()
        self.local_rooms[room_id].add(websocket)

    def disconnect(self, room_id: str, websocket: WebSocket) -> None:
        """웹소켓 연결 종료 시 로컬 방 세션 집합에서 제거하고, 빈 방이면 정리합니다."""
        if room_id in self.local_rooms:
            self.local_rooms[room_id].discard(websocket)
            if not self.local_rooms[room_id]:
                del self.local_rooms[room_id]

    async def broadcast_locally(self, room_id: str, message: dict[str, Any]) -> None:
        """
        현재 서버 인스턴스에 직접 연결되어 있는 지정한 방의 웹소켓 세션들에 메시지를 전송합니다.
        연결이 끊어진 세션(dead connection)은 자동으로 감지하여 정리합니다.
        """
        if room_id not in self.local_rooms:
            return
        dead_connections = set()
        for ws in self.local_rooms[room_id]:
            try:
                await ws.send_json(message)
            except Exception:
                dead_connections.add(ws)
        for dead in dead_connections:
            self.disconnect(room_id, dead)

    async def publish(self, redis: Redis, room_id: str, message: dict[str, Any]) -> None:
        """
        지정한 방(room) 채널로 메시지를 Redis에 발행합니다.
        클러스터 내 모든 서버 노드가 이 메시지를 수신하게 됩니다.
        """
        channel = f"{CHANNEL_PREFIX}{room_id}"
        await redis.publish(channel, json.dumps(message))

    async def start_listener(self, redis: Redis) -> None:
        """
        클러스터 전체의 방 채널(`ws:room:*`)을 패턴 구독(PSUBSCRIBE)하는 백그라운드 리스너를 시작합니다.
        타 노드에서 발행된 메시지를 수신하면 자신의 로컬 웹소켓 세션들에게 전달(broadcast_locally)합니다.
        """
        pubsub = redis.pubsub()
        # Pattern subscribe: 모든 방 채널을 단일 구독으로 수신
        await pubsub.psubscribe(f"{CHANNEL_PREFIX}*")

        async def _reader():
            try:
                async for msg in pubsub.listen():
                    if msg and msg.get("type") == "pmessage":
                        channel_str = msg.get("channel", "")
                        room_id = channel_str.replace(CHANNEL_PREFIX, "")
                        data_str = msg.get("data", "")
                        try:
                            data = json.loads(data_str)
                            await self.broadcast_locally(room_id, data)
                        except Exception:
                            pass
            except asyncio.CancelledError:
                await pubsub.punsubscribe(f"{CHANNEL_PREFIX}*")
                await pubsub.close()

        self._listener_task = asyncio.create_task(_reader())

    async def stop_listener(self) -> None:
        """서버 종료 시 실행 중인 Redis Pub/Sub 백그라운드 리스너 태스크를 취소 및 대기합니다."""
        if self._listener_task and not self._listener_task.done():
            self._listener_task.cancel()
            try:
                await self._listener_task
            except asyncio.CancelledError:
                pass


manager = DistributedPubSubManager()
