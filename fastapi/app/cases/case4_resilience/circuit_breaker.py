import asyncio
import time
from enum import Enum
from typing import Any, Callable, Coroutine


class CircuitState(str, Enum):
    CLOSED = "CLOSED"        # Normal operation: traffic flows through
    OPEN = "OPEN"            # Service failed: calls blocked immediately, fallback returned
    HALF_OPEN = "HALF_OPEN"  # Testing recovery: trial calls allowed


class CircuitBreakerOpenException(Exception):
    """Raised when an operation is attempted while the circuit is open."""


class CircuitBreaker:
    def __init__(
        self,
        name: str,
        failure_threshold: int = 3,
        recovery_timeout_sec: float = 5.0,
    ):
        self.name = name
        self.failure_threshold = failure_threshold
        self.recovery_timeout_sec = recovery_timeout_sec
        self.state: CircuitState = CircuitState.CLOSED
        self.failure_count: int = 0
        self.last_state_change: float = time.time()
        self._lock = asyncio.Lock()

    async def _update_state_if_needed(self) -> None:
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
        async with self._lock:
            await self._update_state_if_needed()

            if self.state == CircuitState.OPEN:
                if fallback:
                    return await fallback(*args, **kwargs)
                raise CircuitBreakerOpenException(f"Circuit '{self.name}' is OPEN. Fast-failing request.")

        # Execute call
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
        if self.state == CircuitState.HALF_OPEN:
            self.state = CircuitState.CLOSED
            self.failure_count = 0
            self.last_state_change = time.time()
        elif self.state == CircuitState.CLOSED:
            self.failure_count = 0

    async def _on_failure(self) -> None:
        self.failure_count += 1
        if self.state == CircuitState.HALF_OPEN or self.failure_count >= self.failure_threshold:
            self.state = CircuitState.OPEN
            self.last_state_change = time.time()

    def get_status(self) -> dict:
        return {
            "name": self.name,
            "state": self.state.value,
            "failure_count": self.failure_count,
            "failure_threshold": self.failure_threshold,
            "recovery_timeout_sec": self.recovery_timeout_sec,
        }
