package com.playground.distributed.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Spring Data Redis 인프라스트럭처 설정 클래스.
 * - StringRedisTemplate: 분산 락, 작업 큐, 처리율 제한에 사용할 문자열 기반 Redis 템플릿
 * - RedisMessageListenerContainer: Case 3 분산 웹소켓 메시지 구독을 위한 리스너 컨테이너
 */
@Configuration
public class RedisConfig {

    /** 문자열 직렬화를 기본으로 사용하는 StringRedisTemplate 빈 등록 */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /** Redis Pub/Sub 메시지 비동기 청취 및 리스너 등록을 담당하는 컨테이너 빈 */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
