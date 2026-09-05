package com.playground.distributed.case3;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class RedisPubSubService implements MessageListener {

    private static final String CHANNEL_PREFIX = "ws:room:";

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final WebSocketChatHandler chatHandler;

    public RedisPubSubService(StringRedisTemplate redisTemplate,
                              RedisMessageListenerContainer listenerContainer,
                              WebSocketChatHandler chatHandler) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.chatHandler = chatHandler;
    }

    @PostConstruct
    public void init() {
        try {
            listenerContainer.addMessageListener(this, new PatternTopic(CHANNEL_PREFIX + "*"));
        } catch (Exception ignored) {}
    }

    public void publish(String roomId, String jsonMessage) {
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + roomId, jsonMessage);
        } catch (Exception ignored) {}
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String roomId = channel.replace(CHANNEL_PREFIX, "");

        chatHandler.broadcastLocally(roomId, body);
    }
}
