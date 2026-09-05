import asyncio
import os
import sys
from redis.asyncio import Redis
import redis.asyncio as aioredis

from app.config import settings
from app.cases.case2_queue.queue_service import (
    JobQueueService,
    STREAM_NAME,
    CONSUMER_GROUP,
)


async def process_task(task_type: str, payload: dict) -> str:
    # Simulate background processing
    duration = payload.get("duration_sec", 1) if isinstance(payload, dict) else 1
    await asyncio.sleep(min(duration, 5))
    return f"Processed '{task_type}' successfully in {duration}s"


async def process_one_job(redis: Redis, worker_id: str, timeout_ms: int = 2000) -> bool:
    service = JobQueueService(redis)
    await service.ensure_consumer_group()

    # Read up to 1 new message for this consumer group
    entries = await redis.xreadgroup(
        groupname=CONSUMER_GROUP,
        consumername=worker_id,
        streams={STREAM_NAME: ">"},
        count=1,
        block=timeout_ms,
    )

    if not entries:
        return False

    for stream, messages in entries:
        for msg_id, data in messages:
            job_id = data.get("job_id")
            if not job_id:
                await redis.xack(STREAM_NAME, CONSUMER_GROUP, msg_id)
                continue

            # Mark as PROCESSING
            await service.update_job_status(job_id, status="PROCESSING", worker_id=worker_id)

            job = await service.get_job(job_id)
            payload = job.get("payload", {}) if job else {}
            task_type = data.get("task_type", "unknown")

            try:
                result_str = await process_task(task_type, payload)
                await service.update_job_status(job_id, status="COMPLETED", worker_id=worker_id, result=result_str)
            except Exception as e:
                await service.update_job_status(job_id, status="FAILED", worker_id=worker_id, result=str(e))
            finally:
                # Acknowledge message
                await redis.xack(STREAM_NAME, CONSUMER_GROUP, msg_id)

    return True


async def run_worker_loop(worker_id: str, redis_url: str):
    print(f"[{worker_id}] Starting worker connecting to {redis_url}...")
    redis = aioredis.from_url(redis_url, encoding="utf-8", decode_responses=True)
    try:
        while True:
            await process_one_job(redis, worker_id, timeout_ms=3000)
    finally:
        await redis.aclose()


if __name__ == "__main__":
    w_id = os.getenv("WORKER_ID", f"worker-{os.getpid()}")
    r_url = os.getenv("REDIS_URL", settings.redis_url)
    try:
        asyncio.run(run_worker_loop(w_id, r_url))
    except KeyboardInterrupt:
        print("\nWorker stopped by user.")
        sys.exit(0)
