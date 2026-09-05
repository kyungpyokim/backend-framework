package com.playground.distributed.case4;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 분산 복원력(Resilience) 비즈니스 서비스.
 * - 서킷 브레이커로 외부 모의 결제 게이트웨이 호출 보호
 * - 장애 발생 시 Fallback 처리 및 서킷 상태 관리
 */
@Service
public class ResilienceService {

    private final CircuitBreaker circuitBreaker = new CircuitBreaker();
    private volatile boolean isHealthy = true;

    /** 모의 외부 서비스 정상 여부 확인 */
    public boolean isHealthy() {
        return isHealthy;
    }

    /** 모의 외부 서비스 장애 상태 설정 */
    public void setHealthy(boolean healthy) {
        isHealthy = healthy;
    }

    /**
     * 외부 결제 서비스를 서킷 브레이커 보호 하에 호출합니다.
     * 외부 서비스가 비정상이거나 서킷이 OPEN인 경우 Fallback 응답을 반환합니다.
     */
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

    /** 서킷 브레이커 상태 및 외부 서비스 정상 여부 조회 */
    public Map<String, Object> getCircuitStatus() {
        Map<String, Object> status = new HashMap<>(circuitBreaker.getStatus());
        status.put("external_service_healthy", isHealthy);
        return status;
    }

    /** 현재 서킷 브레이커 상태(State) 반환 */
    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }
}
