import asyncio
import json
from collections import defaultdict
from typing import Any
from fastapi import WebSocket
from redis.asyncio import Redis

CHANNEL_PREFIX = "ws:room:"


class DistributedPubSubManager:
    def __init__(self):
        # room_id -> set of active WebSockets on THIS specific server instance
        self.local_rooms: dict[str, set[WebSocket]] = defaultdict(set)
        self._listener_task: asyncio.Task | None = None

    async def connect(self, room_id: str, websocket: WebSocket) -> None:
        await websocket.accept()
        self.local_rooms[room_id].add(websocket)

    def disconnect(self, room_id: str, websocket: WebSocket) -> None:
        if room_id in self.local_rooms:
            self.local_rooms[room_id].discard(websocket)
            if not self.local_rooms[room_id]:
                del self.local_rooms[room_id]

    async def broadcast_locally(self, room_id: str, message: dict[str, Any]) -> None:
        """Send message to all local WebSockets in the given room."""
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
        """Publish a message to Redis so ALL server nodes receive it."""
        channel = f"{CHANNEL_PREFIX}{room_id}"
        await redis.publish(channel, json.dumps(message))

    async def start_listener(self, redis: Redis) -> None:
        """Background listener that subscribes to all room channels across the cluster."""
        pubsub = redis.pubsub()
        # Pattern subscribe to all room channels
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
        if self._listener_task and not self._listener_task.done():
            self._listener_task.cancel()
            try:
                await self._listener_task
            except asyncio.CancelledError:
                pass


manager = DistributedPubSubManager()
