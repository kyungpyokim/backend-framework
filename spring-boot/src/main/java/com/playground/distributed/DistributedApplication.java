package com.playground.distributed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 분산 시스템 플레이그라운드 메인 애플리케이션 클래스.
 * - 4대 분산 시스템 패턴 (분산 락, 작업 큐, 웹소켓 Pub/Sub, 서킷 브레이커) 구동
 * - @EnableScheduling: 주기적 작업 스케줄링 활성화
 */
@SpringBootApplication(exclude = {RedisReactiveAutoConfiguration.class})
@EnableScheduling
public class DistributedApplication {
    public static void main(String[] args) {
        SpringApplication.run(DistributedApplication.class, args);
    }
}
