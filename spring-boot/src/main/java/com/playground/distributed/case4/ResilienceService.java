package com.playground.distributed.case4;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("null")
public class ResilienceService {

    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private volatile boolean isHealthy = true;

    public boolean isHealthy() {
        return isHealthy;
    }

    public void setHealthy(boolean healthy) {
        isHealthy = healthy;
    }

    public Map<String, Object> callExternalService() {
        return circuitBreaker.execute(
                () -> {
                    if (!isHealthy) {
                        throw new RuntimeException(
                                "External Payment Gateway is unreachable (503 Service Unavailable)");
                    }
                    return Map.of("status", "SUCCESS", "tx_id", "tx_mock_9999", "amount", 1000);
                },
                () ->
                        Map.of(
                                "status", "FALLBACK",
                                "message",
                                        "Payment system temporarily unavailable. Queued for offline processing.",
                                "fallback_used", true));
    }

    public Map<String, Object> getCircuitStatus() {
        Map<String, Object> status = new HashMap<>(circuitBreaker.getStatus());
        status.put("external_service_healthy", isHealthy);
        return status;
    }

    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }
}
