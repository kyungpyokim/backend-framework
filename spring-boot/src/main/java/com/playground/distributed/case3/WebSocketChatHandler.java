package com.playground.distributed.case3;

import com.playground.distributed.config.AppConfig;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@SuppressWarnings("null")
public class WebSocketChatHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final RedisPubSubService pubSubService;
    private final AppConfig appConfig;

    public WebSocketChatHandler(@Lazy RedisPubSubService pubSubService, AppConfig appConfig) {
        this.pubSubService = pubSubService;
        this.appConfig = appConfig;
    }

    private String extractRoom(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            for (String param : uri.getQuery().split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && "room".equals(pair[0])) {
                    return pair[1];
                }
            }
        }
        return "default";
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String room = extractRoom(session);
        rooms.computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet()).add(session);
        session.getAttributes().put("room", room);

        pubSubService.publish(
                room,
                String.format(
                        "{\"type\":\"system\",\"message\":\"New user connected to room '%s' via node [%s]\",\"node_id\":\"%s\"}",
                        room, appConfig.getNodeId(), appConfig.getNodeId()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String room = (String) session.getAttributes().get("room");
        if (room != null && rooms.containsKey(room)) {
            rooms.get(room).remove(session);
            pubSubService.publish(
                    room,
                    String.format(
                            "{\"type\":\"system\",\"message\":\"User disconnected from room '%s' on node [%s]\",\"node_id\":\"%s\"}",
                            room, appConfig.getNodeId(), appConfig.getNodeId()));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String room = (String) session.getAttributes().get("room");
        if (room != null) {
            pubSubService.publish(
                    room,
                    String.format(
                            "{\"type\":\"chat\",\"content\":\"%s\",\"from_node\":\"%s\"}",
                            message.getPayload(), appConfig.getNodeId()));
        }
    }

    public void broadcastLocally(String room, String message) {
        Set<WebSocketSession> sessions = rooms.get(room);
        if (sessions != null) {
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    try {
                        s.sendMessage(new TextMessage(message));
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
