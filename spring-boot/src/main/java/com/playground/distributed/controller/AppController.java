package com.playground.distributed.controller;

import com.playground.distributed.config.AppConfig;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {

    private final AppConfig appConfig;

    public AppController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
                "status", "ok",
                "app_name", appConfig.getAppName(),
                "version", appConfig.getVersion());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "app_name", appConfig.getAppName(),
                "version", appConfig.getVersion());
    }

    @GetMapping("/cluster/info")
    public Map<String, Object> clusterInfo() {
        return Map.of(
                "node_id", appConfig.getNodeId(),
                "framework", "Spring Boot (Java 21)",
                "app_name", appConfig.getAppName(),
                "version", appConfig.getVersion());
    }
}
