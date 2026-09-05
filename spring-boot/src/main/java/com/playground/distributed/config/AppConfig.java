package com.playground.distributed.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 공통 프로퍼티 설정 빈.
 * application.yml로부터 노드 ID, 앱 이름, 버전 정보를 바인딩합니다.
 */
@Configuration
public class AppConfig {
    @Value("${app.node-id}")
    private String nodeId;

    @Value("${app.name}")
    private String appName;

    @Value("${app.version}")
    private String version;

    public String getNodeId() {
        return nodeId;
    }

    public String getAppName() {
        return appName;
    }

    public String getVersion() {
        return version;
    }
}
