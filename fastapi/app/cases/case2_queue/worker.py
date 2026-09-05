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
    """실제 비즈니스 백그라운드 작업 처리를 시뮬레이션합니다."""
    # Simulate background processing
    duration = payload.get("duration_sec", 1) if isinstance(payload, dict) else 1
    await asyncio.sleep(min(duration, 5))
    return f"Processed '{task_type}' successfully in {duration}s"


async def process_one_job(redis: Redis, worker_id: str, timeout_ms: int = 2000) -> bool:
    """
    Redis Stream Consumer Group으로부터 단일 작업을 가져와 처리하는 핵심 메서드.
    1. XREADGROUP으로 스트림에서 아직 소비되지 않은 신규 메시지('>') 1건을 블로킹 대기(timeout_ms)
    2. 메시지 수신 시 작업 상태를 'PROCESSING'으로 변경
    3. 실제 작업(process_task) 실행 후 성공 시 'COMPLETED', 실패 시 'FAILED'로 갱신
    4. XACK를 Redis에 전송하여 작업 완료를 확인(Acknowledge) 처리
    """
    service = JobQueueService(redis)
    await service.ensure_consumer_group()

    # Read up to 1 new message for this consumer group ('>'는 아직 컨슈머에게 전달되지 않은 새 메시지)
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

            # 작업 상태를 PROCESSING으로 변경하여 다른 모니터링 시스템에 진행 중임을 알림
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
                # 작업이 처리되었으므로 스트림의 대기 목록(Pending Entries List, PEL)에서 제거
                await redis.xack(STREAM_NAME, CONSUMER_GROUP, msg_id)

    return True


async def run_worker_loop(worker_id: str, redis_url: str):
    """
    워커 무한 실행 루프.
    백그라운드에서 주기적으로 스트림을 폴링하며 새 작업이 들어오면 순차적으로 처리합니다.
    """
    print(f"[{worker_id}] Starting worker connecting to {redis_url}...")
    redis = aioredis.from_url(redis_url, encoding="utf-8", decode_responses=True)
    try:
        while True:
            await process_one_job(redis, worker_id, timeout_ms=3000)
    finally:
        await redis.aclose()


if __name__ == "__main__":
    # 워커 단독 프로세스로 실행 시 환경 변수 또는 프로세스 ID 기반으로 워커 ID 부여
    w_id = os.getenv("WORKER_ID", f"worker-{os.getpid()}")
    r_url = os.getenv("REDIS_URL", settings.redis_url)
    try:
        asyncio.run(run_worker_loop(w_id, r_url))
    except KeyboardInterrupt:
        print("\nWorker stopped by user.")
        sys.exit(0)
