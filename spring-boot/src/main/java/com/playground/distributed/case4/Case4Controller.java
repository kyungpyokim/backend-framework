package com.playground.distributed.case4;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playground.distributed.config.AppConfig;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 4: 서비스 간 회복 탄력성(Resilience) 및 서킷 브레이커 REST 컨트롤러.
 * - 모의 외부 결제 시스템 고장 주입 (/case4/external/fault)
 * - 서킷 브레이커 보호 호출 (/case4/call)
 * - 서킷 브레이커 상태 및 메트릭 조회 (/case4/circuit-status)
 */
@RestController
@RequestMapping("/case4")
public class Case4Controller {

    /** 장애 주입 요청 불변 Record DTO (Null Object 패턴 적용) */
    public record FaultRequest(@JsonProperty("is_healthy") Boolean isHealthy) {
        public static final FaultRequest DEFAULT = new FaultRequest(true);

        public FaultRequest {
            isHealthy = isHealthy != null ? isHealthy : true;
        }

        public static FaultRequest ofNullable(FaultRequest req) {
            return req != null ? req : DEFAULT;
        }
    }

    private final ResilienceService resilienceService;
    private final AppConfig appConfig;

    public Case4Controller(ResilienceService resilienceService, AppConfig appConfig) {
        this.resilienceService = resilienceService;
        this.appConfig = appConfig;
    }

    /** [장애 주입] 모의 외부 서비스의 정상/장애 상태를 토글 */
    @PostMapping("/external/fault")
    public Map<String, Object> setFault(@RequestBody(required = false) FaultRequest req) {
        var request = FaultRequest.ofNullable(req);
        resilienceService.setHealthy(request.isHealthy());
        return Map.of(
                "message",
                "External service healthy status set to: " + request.isHealthy(),
                "is_healthy",
                request.isHealthy());
    }

    /**
     * [서킷 보호 호출] 서킷 브레이커를 경유하여 외부 결제 서비스를 호출합니다.
     * 외부 시스템 장애 시 Fallback 응답을 반환합니다.
     */
    @GetMapping("/call")
    public Map<String, Object> callExternal() {
        Map<String, Object> result = resilienceService.callExternalService();
        return Map.of(
                "node_id", appConfig.getNodeId(),
                "circuit_state", resilienceService.getState().name(),
                "result", result);
    }

    /** [서킷 상태 조회] 현재 서킷 브레이커 상태(CLOSED/OPEN/HALF_OPEN), 실패 수 등 조회 */
    @GetMapping("/circuit-status")
    public Map<String, Object> getCircuitStatus() {
        Map<String, Object> status = new HashMap<>(resilienceService.getCircuitStatus());
        status.put("node_id", appConfig.getNodeId());
        return status;
    }
}
