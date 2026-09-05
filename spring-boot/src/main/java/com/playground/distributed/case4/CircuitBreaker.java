package com.playground.distributed.case4;

import java.util.Map;
import java.util.function.Supplier;

public class CircuitBreaker {

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

    public synchronized State getState() {
        updateStateIfNeeded();
        return state;
    }

    private void updateStateIfNeeded() {
        if (state == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastStateChange;
            if (elapsed >= recoveryTimeoutMs) {
                state = State.HALF_OPEN;
                lastStateChange = System.currentTimeMillis();
            }
        }
    }

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

    private void onSuccess() {
        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            failureCount = 0;
            lastStateChange = System.currentTimeMillis();
        } else if (state == State.CLOSED) {
            failureCount = 0;
        }
    }

    private void onFailure() {
        failureCount++;
        if (state == State.HALF_OPEN || failureCount >= failureThreshold) {
            state = State.OPEN;
            lastStateChange = System.currentTimeMillis();
        }
    }

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
