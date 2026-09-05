import asyncio
import time
from enum import Enum
from typing import Any, Callable, Coroutine


class CircuitState(str, Enum):
    """
    서킷 브레이커의 3가지 핵심 상태.
    - CLOSED: 정상 상태. 모든 트래픽이 원격 서비스로 정상 전달됩니다.
    - OPEN: 장애 상태. 원격 서비스 장애 감지 후 호출을 즉시 차단(Fast-Fail)하고 Fallback을 반환합니다.
    - HALF_OPEN: 시험 회복 상태. 회복 타임아웃 경과 후 서비스가 복구되었는지 시험 호출을 허용합니다.
    """
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


class CircuitBreakerOpenException(Exception):
    """서킷이 OPEN 상태일 때 호출이 차단되었음을 나타내는 예외."""


class CircuitBreaker:
    """
    분산 시스템 회복 탄력성(Resilience)을 위한 서킷 브레이커 패턴 구현체.
    외부 장애 서비스로의 지속적인 요청을 차단하여 연쇄 장애(Cascading Failure)를 방지하고 빠른 실패를 유도합니다.
    """

    def __init__(
        self,
        name: str,
        failure_threshold: int = 3,
        recovery_timeout_sec: float = 5.0,
    ):
        """
        :param name: 서킷 브레이커 식별 이름
        :param failure_threshold: OPEN 상태로 전이하기 위한 연속 실패 횟수 임계치
        :param recovery_timeout_sec: OPEN 상태에서 HALF_OPEN으로 시험 전환하기까지의 쿨다운 대기 시간(초)
        """
        self.name = name
        self.failure_threshold = failure_threshold
        self.recovery_timeout_sec = recovery_timeout_sec
        self.state: CircuitState = CircuitState.CLOSED
        self.failure_count: int = 0
        self.last_state_change: float = time.time()
        self._lock = asyncio.Lock()

    async def _update_state_if_needed(self) -> None:
        """OPEN 상태에서 recovery_timeout_sec 시간이 지났으면 HALF_OPEN 상태로 자동 전환"""
        if self.state == CircuitState.OPEN:
            elapsed = time.time() - self.last_state_change
            if elapsed >= self.recovery_timeout_sec:
                self.state = CircuitState.HALF_OPEN
                self.last_state_change = time.time()

    async def call(
        self,
        func: Callable[..., Coroutine[Any, Any, Any]],
        fallback: Callable[..., Coroutine[Any, Any, Any]] | None = None,
        *args,
        **kwargs,
    ) -> Any:
        """
        서킷 브레이커 보호 하에 대상 비동기 함수를 실행합니다.
        - 서킷이 OPEN인 경우: 함수를 호출하지 않고 fallback을 즉시 실행하거나 CircuitBreakerOpenException 발생
        - 함수 실행 성공 시: 실패 카운트 초기화 및 HALF_OPEN 상태였다면 CLOSED로 복구
        - 함수 실행 실패 시: 실패 카운트 증가 및 임계치 도달 시 즉시 OPEN으로 전환
        """
        async with self._lock:
            await self._update_state_if_needed()

            if self.state == CircuitState.OPEN:
                if fallback:
                    return await fallback(*args, **kwargs)
                raise CircuitBreakerOpenException(f"Circuit '{self.name}' is OPEN. Fast-failing request.")

        # 보호 대상 원격 호출 실행
        try:
            result = await func(*args, **kwargs)
            async with self._lock:
                await self._on_success()
            return result
        except Exception as exc:
            async with self._lock:
                await self._on_failure()
            if fallback:
                return await fallback(*args, **kwargs)
            raise

    async def _on_success(self) -> None:
        """호출 성공 처리: HALF_OPEN 상태였다면 CLOSED로 전이하여 정상 상태로 회복"""
        if self.state == CircuitState.HALF_OPEN:
            self.state = CircuitState.CLOSED
            self.failure_count = 0
            self.last_state_change = time.time()
        elif self.state == CircuitState.CLOSED:
            self.failure_count = 0

    async def _on_failure(self) -> None:
        """호출 실패 처리: 임계치 도달 시 OPEN으로 전환하여 후속 요청을 차단"""
        self.failure_count += 1
        if self.state == CircuitState.HALF_OPEN or self.failure_count >= self.failure_threshold:
            self.state = CircuitState.OPEN
            self.last_state_change = time.time()

    def get_status(self) -> dict:
        """현재 서킷 브레이커의 메트릭 및 상태 정보 반환"""
        return {
            "name": self.name,
            "state": self.state.value,
            "failure_count": self.failure_count,
            "failure_threshold": self.failure_threshold,
            "recovery_timeout_sec": self.recovery_timeout_sec,
        }
