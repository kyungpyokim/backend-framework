import asyncio
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.config import settings
from app.cases.case4_resilience.circuit_breaker import CircuitBreaker, CircuitBreakerOpenException

router = APIRouter(prefix="/case4", tags=["Case 4: Inter-Service Resilience & Circuit Breaker"])

# 결제 게이트웨이(외부 의존 서비스) 모의 호출을 보호하는 서킷 브레이커 싱글톤
payment_circuit_breaker = CircuitBreaker(
    name="payment-gateway",
    failure_threshold=3,
    recovery_timeout_sec=4.0,
)

# 모의 외부 서비스의 장애 상태 (True: 정상, False: 503 에러 발생)
_external_service_healthy = True


class FaultToggleRequest(BaseModel):
    """외부 서비스 장애 주입 요청 DTO"""
    is_healthy: bool


async def mock_external_payment_service() -> dict:
    """외부 결제 서비스 호출을 흉내 내는 모의 함수 (네트워크 지연 및 장애 시뮬레이션)."""
    # Simulate network latency
    await asyncio.sleep(0.05)
    if not _external_service_healthy:
        raise ConnectionError("External Payment Gateway is unreachable (503 Service Unavailable)")
    return {"status": "SUCCESS", "tx_id": "tx_mock_9999", "amount": 1000}


async def fallback_payment_handler() -> dict:
    """서킷 OPEN 또는 일시적 오류 시 반환되는 안전한 대체(Fallback) 응답 핸들러."""
    return {
        "status": "FALLBACK",
        "message": "Payment system temporarily unavailable. Queued for offline processing.",
        "fallback_used": True,
    }


@router.post("/external/fault")
async def toggle_external_fault(req: FaultToggleRequest) -> dict:
    """
    [장애 주입 엔드포인트] 외부 결제 시스템의 정상/고장 상태를 인위적으로 조작합니다.
    """
    global _external_service_healthy
    _external_service_healthy = req.is_healthy
    return {
        "message": f"External service healthy status set to: {_external_service_healthy}",
        "is_healthy": _external_service_healthy,
    }


@router.get("/call")
async def call_external_with_protection() -> dict:
    """
    [서킷 브레이커 보호 호출]
    - 서킷 브레이커를 거쳐 외부 결제 서비스를 호출합니다.
    - 외부 서비스 장애 시 3회 실패 후 서킷이 OPEN되어 이후 요청은 즉시 Fallback을 반환합니다.
    """
    try:
        response = await payment_circuit_breaker.call(
            func=mock_external_payment_service,
            fallback=fallback_payment_handler,
        )
        return {
            "node_id": settings.node_id,
            "circuit_state": payment_circuit_breaker.state.value,
            "result": response,
        }
    except CircuitBreakerOpenException as e:
        raise HTTPException(status_code=503, detail=str(e))


@router.get("/circuit-status")
async def get_circuit_status() -> dict:
    """[서킷 브레이커 상태 모니터링] 현재 서킷 상태(CLOSED/OPEN/HALF_OPEN), 실패 횟수 등을 조회합니다."""
    status_info = payment_circuit_breaker.get_status()
    status_info["node_id"] = settings.node_id
    status_info["external_service_healthy"] = _external_service_healthy
    return status_info
