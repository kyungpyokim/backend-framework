import pytest
from httpx import AsyncClient
from redis.asyncio import Redis

from app.cases.case2_queue.worker import process_one_job


@pytest.mark.asyncio
async def test_job_enqueue_and_worker_processing(client: AsyncClient, fake_redis: Redis):
    # 1. Enqueue new job
    res = await client.post(
        "/case2/jobs",
        json={"task_type": "resize_image", "payload": {"width": 800, "height": 600, "duration_sec": 0}},
    )
    assert res.status_code == 202
    job_id = res.json()["job_id"]
    assert res.json()["status"] == "PENDING"

    # 2. Verify state before worker processing
    job_before = await client.get(f"/case2/jobs/{job_id}")
    assert job_before.status_code == 200
    assert job_before.json()["status"] == "PENDING"

    # 3. Simulate 1 execution cycle of distributed worker
    processed = await process_one_job(fake_redis, worker_id="test-worker-1", timeout_ms=1000)
    assert processed is True

    # 4. Verify state after worker processing
    job_after = await client.get(f"/case2/jobs/{job_id}")
    assert job_after.status_code == 200
    data = job_after.json()
    assert data["status"] == "COMPLETED"
    assert data["worker_id"] == "test-worker-1"
    assert "Processed 'resize_image'" in data["result"]
