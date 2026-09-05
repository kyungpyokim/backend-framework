package com.playground.distributed.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

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
