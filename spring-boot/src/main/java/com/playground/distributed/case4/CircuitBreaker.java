package com.playground.distributed.case4;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 스레드 안전한(synchronized) 서킷 브레이커 패턴 구현체.
 * CLOSED(정상), OPEN(차단), HALF_OPEN(시험 회복) 3단계 상태 머신을 관리하여
 * 외부 원격 서비스 장애 시 연쇄 장애(Cascading Failure)를 방지하고 빠른 실패(Fast-Fail)를 유도합니다.
 */
public class CircuitBreaker {

    /** 서킷 브레이커 상태 열거형 */
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private State state = State.CLOSED;
    private int failureCount = 0;
    private final int failureThreshold = 3;
    private final long recoveryTimeoutMs = 4000;
    private long lastStateChange = System.currentTimeMillis();

    /** 현재 서킷 브레이커 상태를 갱신 후 반환 */
    public synchronized State getState() {
        updateStateIfNeeded();
        return state;
    }

    /** OPEN 상태에서 쿨다운 대기 시간(recoveryTimeoutMs)이 지났으면 HALF_OPEN으로 전이 */
    private void updateStateIfNeeded() {
        if (state == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastStateChange;
            if (elapsed >= recoveryTimeoutMs) {
                state = State.HALF_OPEN;
                lastStateChange = System.currentTimeMillis();
            }
        }
    }

    /**
     * 서킷 브레이커 보호 하에 주어진 작업을 실행합니다.
     * - OPEN 상태인 경우 호출을 차단하고 fallback 실행
     * - 작업 성공 시 실패 카운트 리셋 및 CLOSED 복구
     * - 작업 실패 시 실패 카운트 증가 및 임계치 도달 시 OPEN으로 전이
     */
    public synchronized <T> T execute(Supplier<T> action, Supplier<T> fallback) {
        updateStateIfNeeded();

        if (state == State.OPEN) {
            if (fallback != null) {
                return fallback.get();
            }
            throw new RuntimeException("Circuit is OPEN (Fast-Fail)");
        }

        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            if (fallback != null) {
                return fallback.get();
            }
            throw e;
        }
    }

    /** 성공 처리: HALF_OPEN 상태였다면 CLOSED로 완전 복구 */
    private void onSuccess() {
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            failureCount = 0;
            lastStateChange = System.currentTimeMillis();
        } else if (state == State.CLOSED) {
            failureCount = 0;
        }
    }

    /** 실패 처리: 임계치(3회) 도달 시 OPEN으로 전환하여 후속 호출 차단 */
    private void onFailure() {
        failureCount++;
        if (state == State.HALF_OPEN || failureCount >= failureThreshold) {
            state = State.OPEN;
            lastStateChange = System.currentTimeMillis();
        }
    }

    /** 서킷 상태 및 실패 통계 메트릭 반환 */
    public synchronized Map<String, Object> getStatus() {
        return Map.of(
                "state",
                getState().name(),
                "failure_count",
                failureCount,
                "failure_threshold",
                failureThreshold,
                "recovery_timeout_sec",
                recoveryTimeoutMs / 1000.0);
    }
}
