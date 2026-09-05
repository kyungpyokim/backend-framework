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

@RestController
@RequestMapping("/case4")
public class Case4Controller {

    public record FaultRequest(@JsonProperty("is_healthy") Boolean isHealthy) {
        public boolean resolveIsHealthy() {
            return isHealthy != null ? isHealthy : true;
        }
    }

    private final ResilienceService resilienceService;
    private final AppConfig appConfig;

    public Case4Controller(ResilienceService resilienceService, AppConfig appConfig) {
        this.resilienceService = resilienceService;
        this.appConfig = appConfig;
    }

    @PostMapping("/external/fault")
    public Map<String, Object> setFault(@RequestBody(required = false) FaultRequest req) {
        boolean healthy = req != null ? req.resolveIsHealthy() : true;
        resilienceService.setHealthy(healthy);
        return Map.of(
                "message",
                "External service healthy status set to: " + healthy,
                "is_healthy",
                healthy);
    }

    @GetMapping("/call")
    public Map<String, Object> callExternal() {
        Map<String, Object> result = resilienceService.callExternalService();
        return Map.of(
                "node_id", appConfig.getNodeId(),
                "circuit_state", resilienceService.getState().name(),
                "result", result);
    }

    @GetMapping("/circuit-status")
    public Map<String, Object> getCircuitStatus() {
        Map<String, Object> status = new HashMap<>(resilienceService.getCircuitStatus());
        status.put("node_id", appConfig.getNodeId());
        return status;
    }
}
