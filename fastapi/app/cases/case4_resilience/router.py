import asyncio
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.config import settings
from app.cases.case4_resilience.circuit_breaker import CircuitBreaker, CircuitBreakerOpenException

router = APIRouter(prefix="/case4", tags=["Case 4: Inter-Service Resilience & Circuit Breaker"])

# Shared circuit breaker instance for external payment gateway simulation
payment_circuit_breaker = CircuitBreaker(
    name="payment-gateway",
    failure_threshold=3,
    recovery_timeout_sec=4.0,
)

# Simulated external service state
_external_service_healthy = True


class FaultToggleRequest(BaseModel):
    is_healthy: bool


async def mock_external_payment_service() -> dict:
    # Simulate network latency
    await asyncio.sleep(0.05)
    if not _external_service_healthy:
        raise ConnectionError("External Payment Gateway is unreachable (503 Service Unavailable)")
    return {"status": "SUCCESS", "tx_id": "tx_mock_9999", "amount": 1000}


async def fallback_payment_handler() -> dict:
    return {
        "status": "FALLBACK",
        "message": "Payment system temporarily unavailable. Queued for offline processing.",
        "fallback_used": True,
    }


@router.post("/external/fault")
async def toggle_external_fault(req: FaultToggleRequest) -> dict:
    global _external_service_healthy
    _external_service_healthy = req.is_healthy
    return {
        "message": f"External service healthy status set to: {_external_service_healthy}",
        "is_healthy": _external_service_healthy,
    }


@router.get("/call")
async def call_external_with_protection() -> dict:
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
    status_info = payment_circuit_breaker.get_status()
    status_info["node_id"] = settings.node_id
    status_info["external_service_healthy"] = _external_service_healthy
    return status_info
