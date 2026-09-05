package com.playground.distributed.controller;

import com.playground.distributed.config.AppConfig;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기본 헬스체크 및 클러스터 노드 정보 컨트롤러.
 */
@RestController
public class AppController {

    private final AppConfig appConfig;

    public AppController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /** 루트 기본 응답 */
    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
                "status", "ok",
                "app_name", appConfig.getAppName(),
                "version", appConfig.getVersion());
    }

    /** 로드밸런서(Nginx) 헬스체크 엔드포인트 */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "app_name", appConfig.getAppName(),
                "version", appConfig.getVersion());
    }

    /** 로드밸런싱 환경에서 현재 요청을 처리한 Spring Boot 노드 정보 반환 */
    @GetMapping("/cluster/info")
    public Map<String, Object> clusterInfo() {
        return Map.of(
                "node_id", appConfig.getNodeId(),
                "framework", "Spring Boot (Java 21)",
                "app_name", appConfig.getAppName(),
                "version", appConfig.getVersion());
    }
}
