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
    task_type: str = "heavy_computation"
    payload: dict[str, Any] = {"duration_sec": 2, "input_data": "sample_data"}


class JobResponse(BaseModel):
    node_id: str
    job_id: str
    status: str
    task_type: str
    payload: Any | None = None
    result: str | None = None
    worker_id: str | None = None


@router.post("/jobs", status_code=status.HTTP_202_ACCEPTED)
async def submit_job(req: CreateJobRequest, redis: RedisDep) -> dict:
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
