package com.playground.distributed.case4;

import com.playground.distributed.config.AppConfig;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/case4")
public class Case4Controller {

    private final ResilienceService resilienceService;
    private final AppConfig appConfig;

    public Case4Controller(ResilienceService resilienceService, AppConfig appConfig) {
        this.resilienceService = resilienceService;
        this.appConfig = appConfig;
    }

    @PostMapping("/external/fault")
    public Map<String, Object> setFault(@RequestBody Map<String, Boolean> body) {
        boolean healthy = body.getOrDefault("is_healthy", true);
        resilienceService.setHealthy(healthy);
        return Map.of(
            "message", "External service healthy status set to: " + healthy,
            "is_healthy", healthy
        );
    }

    @GetMapping("/call")
    public Map<String, Object> callExternal() {
        Map<String, Object> result = resilienceService.callExternalService();
        return Map.of(
            "node_id", appConfig.getNodeId(),
            "circuit_state", resilienceService.getState().name(),
            "result", result
        );
    }

    @GetMapping("/circuit-status")
    public Map<String, Object> getCircuitStatus() {
        Map<String, Object> status = new HashMap<>(resilienceService.getCircuitStatus());
        status.put("node_id", appConfig.getNodeId());
        return status;
    }
}
