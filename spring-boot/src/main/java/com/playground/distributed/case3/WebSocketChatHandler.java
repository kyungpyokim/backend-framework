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

/**
 * Spring 웹소켓 세션 관리 및 양방향 채팅 핸들러.
 * - 클라이언트 접속/해제 시 로컬 ConcurrentHashMap에 세션 저장
 * - 메시지 수신 시 RedisPubSubService를 통해 클러스터 전체 노드로 발행
 */
@Component
public class WebSocketChatHandler extends TextWebSocketHandler {

    // 방 ID -> 현재 서버 노드에 직접 연결된 WebSocket 세션 집합
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final RedisPubSubService pubSubService;
    private final AppConfig appConfig;

    public WebSocketChatHandler(@Lazy RedisPubSubService pubSubService, AppConfig appConfig) {
        this.pubSubService = pubSubService;
        this.appConfig = appConfig;
    }

    /** WebSocket 세션의 쿼리 스트링(?room=xxx)으로부터 방 ID 추출 */
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

    /** 웹소켓 연결 성공 시 방 세션 등록 및 입장 시스템 메시지 발행 */
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

    /** 웹소켓 연결 종료 시 세션 제거 및 퇴장 시스템 메시지 발행 */
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

    /** 클라이언트 텍스트 수신 시 Redis 채널로 채팅 메시지 발행 */
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

    /** 현재 노드에 연결된 지정 방의 모든 세션에 로컬 텍스트 메시지 브로드캐스트 */
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
