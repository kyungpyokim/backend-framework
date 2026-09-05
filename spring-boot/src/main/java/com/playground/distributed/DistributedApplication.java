package com.playground.distributed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;

@SpringBootApplication(exclude = {RedisReactiveAutoConfiguration.class})
@EnableScheduling
public class DistributedApplication {
    public static void main(String[] args) {
        SpringApplication.run(DistributedApplication.class, args);
    }
}
