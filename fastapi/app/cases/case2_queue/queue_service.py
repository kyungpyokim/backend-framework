import json
import time
import uuid
from typing import Any
from redis.asyncio import Redis

# Redis Stream 키 및 Consumer Group 이름 정의
STREAM_NAME = "stream:jobs"
CONSUMER_GROUP = "group:workers"


class JobQueueService:
    """
    Redis Stream 및 Hash 기반의 분산 비동기 작업 큐(Job Queue) 서비스.
    - 생산자(API 서버)는 작업을 스트림에 넣고(XADD), 상태를 Hash에 영속화합니다.
    - 소비자(Worker)는 Consumer Group 단위로 작업을 분배받아 중복 없이 병렬 처리합니다.
    """

    def __init__(self, redis: Redis):
        self.redis = redis

    async def ensure_consumer_group(self) -> None:
        """
        Redis Stream에 대한 Consumer Group이 존재하는지 확인하고, 없으면 생성(mkstream=True)합니다.
        이미 생성된 경우 Redis가 던지는 BUSYGROUP 예외는 정상으로 간주하고 무시합니다.
        """
        try:
            await self.redis.xgroup_create(
                name=STREAM_NAME,
                groupname=CONSUMER_GROUP,
                id="0",
                mkstream=True,
            )
        except Exception as e:
            # Group already exists (BUSYGROUP)
            if "BUSYGROUP" not in str(e):
                raise

    async def enqueue_job(self, task_type: str, payload: dict[str, Any]) -> str:
        """
        신규 비동기 작업을 생성하여 큐에 인큐합니다.
        1. 작업 ID(UUID) 생성
        2. Redis Hash (job:{job_id})에 메타데이터(상태 PENDING, 페이로드 등) 저장
        3. Redis Stream (stream:jobs)에 메시지 발행 (XADD)
        """
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
        # Redis Hash에 작업 상세 상태 영속화
        await self.redis.hset(f"job:{job_id}", mapping=job_data)
        # Redis Stream에 메시지 푸시 (워커들이 읽어갈 수 있도록 큐잉)
        await self.redis.xadd(
            STREAM_NAME,
            {"job_id": job_id, "task_type": task_type},
        )
        return job_id

    async def get_job(self, job_id: str) -> dict[str, Any] | None:
        """작업 ID로 작업의 현재 상태(상태, 작업자, 처리 결과 등)를 Redis Hash에서 조회합니다."""
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
        """
        워커 처리 단계에 따라 작업 상태를 갱신합니다 (예: PROCESSING, COMPLETED, FAILED).
        """
        mapping = {
            "status": status,
            "updated_at": str(time.time()),
        }
        if worker_id:
            mapping["worker_id"] = worker_id
        if result:
            mapping["result"] = result
        await self.redis.hset(f"job:{job_id}", mapping=mapping)
