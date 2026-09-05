package com.playground.distributed.case3;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub 메시지 발행 및 수신(MessageListener) 서비스.
 * - 특정 방(room)으로 발행된 메시지를 Redis 채널(ws:room:*)을 통해 클러스터 전체 노드로 브로드캐스트
 * - 수신된 메시지는 로컬 웹소켓 세션들에게 전달(broadcastLocally)
 */
@Service
public class RedisPubSubService implements MessageListener {

    private static final String CHANNEL_PREFIX = "ws:room:";

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final WebSocketChatHandler chatHandler;

    public RedisPubSubService(
            StringRedisTemplate redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            WebSocketChatHandler chatHandler) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.chatHandler = chatHandler;
    }

    /** 빈 초기화 시 ws:room:* 패턴 토픽에 대한 메시지 리스너 등록 */
    @PostConstruct
    public void init() {
        try {
            listenerContainer.addMessageListener(this, new PatternTopic(CHANNEL_PREFIX + "*"));
        } catch (Exception ignored) {
        }
    }

    /** Redis 채널로 메시지 발행 */
    public void publish(String roomId, String jsonMessage) {
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + roomId, jsonMessage);
        } catch (Exception ignored) {
        }
    }

    /** 타 노드 또는 본인이 발행한 메시지를 수신하여 로컬 웹소켓 클라이언트들에게 브로드캐스트 */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String roomId = channel.replace(CHANNEL_PREFIX, "");

        chatHandler.broadcastLocally(roomId, body);
    }
}
