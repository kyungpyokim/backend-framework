from typing import Annotated, Any
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from redis.asyncio import Redis

from app.config import settings
from app.core.redis import get_redis
from app.cases.case2_queue.queue_service import JobQueueService

router = APIRouter(prefix="/case2", tags=["Case 2: Async Job Queue & Workers"])
RedisDep = Annotated[Redis, Depends(get_redis)]


class CreateJobRequest(BaseModel):
    """비동기 작업 생성 요청 DTO"""
    task_type: str = "heavy_computation"
    payload: dict[str, Any] = {"duration_sec": 2, "input_data": "sample_data"}


class JobResponse(BaseModel):
    """비동기 작업 상태 및 결과 응답 DTO"""
    node_id: str
    job_id: str
    status: str
    task_type: str
    payload: Any | None = None
    result: str | None = None
    worker_id: str | None = None


@router.post("/jobs", status_code=status.HTTP_202_ACCEPTED)
async def submit_job(req: CreateJobRequest, redis: RedisDep) -> dict:
    """
    [작업 인큐] 새로운 비동기 백그라운드 작업을 큐에 등록합니다.
    - HTTP 202 Accepted 응답과 함께 발급된 job_id를 반환합니다.
    - 클라이언트는 반환된 ID로 작업 완료 여부를 비동기 폴링할 수 있습니다.
    """
    service = JobQueueService(redis)
    job_id = await service.enqueue_job(req.task_type, req.payload)
    return {
        "node_id": settings.node_id,
        "job_id": job_id,
        "status": "PENDING",
        "message": "Job accepted and enqueued. Poll GET /case2/jobs/{job_id} for status.",
    }


@router.get("/jobs/{job_id}", response_model=JobResponse)
async def check_job(job_id: str, redis: RedisDep) -> JobResponse:
    """
    [작업 상태 조회] 작업의 진행 상태(PENDING, PROCESSING, COMPLETED, FAILED) 및 결과를 조회합니다.
    """
    service = JobQueueService(redis)
    job = await service.get_job(job_id)
    if not job:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Job not found")
    return JobResponse(
        node_id=settings.node_id,
        job_id=job["job_id"],
        status=job.get("status", "UNKNOWN"),
        task_type=job.get("task_type", ""),
        payload=job.get("payload"),
        result=job.get("result"),
        worker_id=job.get("worker_id"),
    )
