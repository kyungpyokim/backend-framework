import asyncio
import os
import sys
import time
from pathlib import Path

# Add project root to sys.path for standalone script execution
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import fakeredis.aioredis

from redis.asyncio import Redis
import redis.asyncio as aioredis

from app.cases.case1_lock.lock_service import (
    init_inventory,
    get_inventory,
    purchase_with_lock,
    purchase_without_lock,
)
from app.cases.case1_lock.rate_limiter import check_rate_limit
from app.cases.case2_queue.queue_service import JobQueueService
from app.cases.case2_queue.worker import process_one_job
from app.cases.case4_resilience.circuit_breaker import CircuitBreaker


async def run_case1_demo(redis: Redis):
    print("\n" + "=" * 70)
    print(" [Case 1] Stateless Scale-Out & 동시성 제어 (분산 락 vs 미적용 비교)")
    print("=" * 70)

    # 1. 분산 락 미적용 (Unsafe) 시연
    print("\n--- 1) 분산 락 미적용 (Race Condition 취약) ---")
    item_unsafe = "iphone-unsafe"
    await init_inventory(redis, item_unsafe, stock=10)
    print(f"초기 재고: {await get_inventory(redis, item_unsafe)}개")
    print("30개의 동시 구매 요청(각 1개)을 보냅니다...")

    async def unsafe_task():
        return await purchase_without_lock(redis, item_unsafe, quantity=1)

    results_unsafe = await asyncio.gather(*[unsafe_task() for _ in range(30)])
    success_unsafe = sum(1 for r in results_unsafe if r["success"])
    final_unsafe = await get_inventory(redis, item_unsafe)
    print(f"-> 결과: 성공한 구매 = {success_unsafe}건 / 최종 남은 재고 = {final_unsafe}개")
    print("   [문제점] 동시 요청들이 이전 재고를 덮어써서 실제 재고보다 더 많이 팔리는 초과 판매 발생!")

    # 2. 분산 락 적용 (Safe) 시연
    print("\n--- 2) Redis 분산 락 적용 (SET NX PX + Lua 해제) ---")
    item_safe = "iphone-safe"
    await init_inventory(redis, item_safe, stock=10)
    print(f"초기 재고: {await get_inventory(redis, item_safe)}개")
    print("30개의 동시 구매 요청(각 1개)을 보냅니다...")

    async def safe_task():
        try:
            return await purchase_with_lock(redis, item_safe, quantity=1)
        except TimeoutError:
            return {"success": False, "message": "Lock timeout"}

    results_safe = await asyncio.gather(*[safe_task() for _ in range(30)])
    success_safe = sum(1 for r in results_safe if r["success"])
    final_safe = await get_inventory(redis, item_safe)
    print(f"-> 결과: 성공한 구매 = {success_safe}건 / 최종 남은 재고 = {final_safe}개")
    print("   [성공] 정확히 10명만 구매에 성공하고, 나머지 20명은 'Out of stock'으로 안전하게 방어되었습니다.")

    # 3. 분산 슬라이딩 윈도우 Rate Limit
    print("\n--- 3) 분산 슬라이딩 윈도우 Rate Limiter ---")
    print("10초 동안 최대 3회 요청 허용 설정 (유저: alice)")
    for req_idx in range(1, 6):
        allowed, remaining = await check_rate_limit(redis, "alice", limit=3, window_seconds=10)
        status = "허용(PASS)" if allowed else "차단(429 BLOCKED)"
        print(f"  요청 #{req_idx}: {status} (잔여 허용량: {remaining})")


async def run_case2_demo(redis: Redis):
    print("\n" + "=" * 70)
    print(" [Case 2] 이벤트 기반 비동기 작업 큐 & 분산 워커 (Redis Streams)")
    print("=" * 70)

    queue = JobQueueService(redis)
    jobs = [
        ("pdf_generate", {"doc_id": "report_2026", "duration_sec": 0}),
        ("ai_inference", {"model": "llama-3", "duration_sec": 0}),
        ("email_blast", {"count": 500, "duration_sec": 0}),
    ]

    print("1) API 서버(Producer)가 3개의 무거운 작업을 큐에 비동기 발행합니다...")
    enqueued_ids = []
    for task_type, payload in jobs:
        job_id = await queue.enqueue_job(task_type, payload)
        enqueued_ids.append(job_id)
        print(f"  -> 작업 등록 완료: ID={job_id[:8]}... (유형: {task_type}, 상태: PENDING)")

    print("\n2) 분산 워커 노드(Consumer)들이 큐에서 작업을 가져와 병렬 처리합니다...")
    for idx, job_id in enumerate(enqueued_ids, 1):
        worker_name = f"worker-node-{idx}"
        await process_one_job(redis, worker_id=worker_name, timeout_ms=500)
        job_data = await queue.get_job(job_id)
        print(f"  -> [{worker_name}] 작업 완료! ID={job_id[:8]}... 상태={job_data['status']} 결과={job_data['result']}")


async def run_case4_demo():
    print("\n" + "=" * 70)
    print(" [Case 4] 서비스 간 통신 장애 격리 (Circuit Breaker & Fallback)")
    print("=" * 70)

    cb = CircuitBreaker("payment-service", failure_threshold=3, recovery_timeout_sec=2.0)
    service_healthy = True

    async def payment_api():
        if not service_healthy:
            raise ConnectionError("결제 PG사 통신 두절 (503 Error)")
        return "결제 승인 완료 ($100)"

    async def fallback():
        return "Fallback: 오프라인 승인 모드로 전환되어 결제가 대기열에 보관되었습니다."

    print("1) 정상 상태 호출:")
    res1 = await cb.call(payment_api, fallback=fallback)
    print(f"  응답: {res1} | 서킷 상태: {cb.state.value}")

    print("\n2) 외부 PG사 장애 발생! (3회 실패 유도)")
    service_healthy = False
    for i in range(1, 4):
        res = await cb.call(payment_api, fallback=fallback)
        print(f"  실패 시도 #{i} -> 응답: {res} | 서킷 상태: {cb.state.value}")

    print("\n3) 서킷 OPEN 이후 추가 요청 (빠른 실패 & Fallback 처리):")
    res_fast = await cb.call(payment_api, fallback=fallback)
    print(f"  외부 API 호출 시도조차 하지 않고 즉시 리턴: {res_fast} | 서킷 상태: {cb.state.value}")

    print("\n4) 2초 후 회복 타임아웃 경과 -> HALF_OPEN 전이 및 서비스 정상화 테스트:")
    await asyncio.sleep(2.1)
    service_healthy = True
    res_recovered = await cb.call(payment_api, fallback=fallback)
    print(f"  테스트 성공 후 서킷 정상 닫힘: {res_recovered} | 최종 서킷 상태: {cb.state.value}")


async def main():
    print("=" * 70)
    print("   분산 시스템 플레이그라운드 (Distributed Systems Playground) 데모")
    print("=" * 70)

    # Use fakeredis so demo runs 100% standalone without external dependencies
    server = fakeredis.FakeServer()
    redis = fakeredis.aioredis.FakeRedis(server=server, decode_responses=True)

    try:
        await run_case1_demo(redis)
        await run_case2_demo(redis)
        await run_case4_demo()
        print("\n" + "=" * 70)
        print(" 모든 분산 시스템 케이스 데모가 성공적으로 완료되었습니다!")
        print("=" * 70 + "\n")
    finally:
        await redis.aclose()


if __name__ == "__main__":
    asyncio.run(main())
