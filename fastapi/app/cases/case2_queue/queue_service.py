import json
import time
import uuid
from typing import Any
from redis.asyncio import Redis

STREAM_NAME = "stream:jobs"
CONSUMER_GROUP = "group:workers"


class JobQueueService:
    def __init__(self, redis: Redis):
        self.redis = redis

    async def ensure_consumer_group(self) -> None:
        try:
            await self.redis.xgroup_create(
                name=STREAM_NAME,
                groupname=CONSUMER_GROUP,
                id="0",
                mkstream=True,
            )
        except Exception as e:
            # Group already exists
            if "BUSYGROUP" not in str(e):
                raise

    async def enqueue_job(self, task_type: str, payload: dict[str, Any]) -> str:
        await self.ensure_consumer_group()
        job_id = str(uuid.uuid4())
        job_data = {
            "job_id": job_id,
            "task_type": task_type,
            "payload": json.dumps(payload),
            "status": "PENDING",
            "result": "",
            "created_at": str(time.time()),
            "updated_at": str(time.time()),
            "worker_id": "",
        }
        # Save job state in Redis Hash
        await self.redis.hset(f"job:{job_id}", mapping=job_data)
        # Push message into Redis Stream
        await self.redis.xadd(
            STREAM_NAME,
            {"job_id": job_id, "task_type": task_type},
        )
        return job_id

    async def get_job(self, job_id: str) -> dict[str, Any] | None:
        data = await self.redis.hgetall(f"job:{job_id}")
        if not data:
            return None
        if "payload" in data and isinstance(data["payload"], str):
            try:
                data["payload"] = json.loads(data["payload"])
            except Exception:
                pass
        return data

    async def update_job_status(
        self,
        job_id: str,
        status: str,
        worker_id: str = "",
        result: str = "",
    ) -> None:
        mapping = {
            "status": status,
            "updated_at": str(time.time()),
        }
        if worker_id:
            mapping["worker_id"] = worker_id
        if result:
            mapping["result"] = result
        await self.redis.hset(f"job:{job_id}", mapping=mapping)
