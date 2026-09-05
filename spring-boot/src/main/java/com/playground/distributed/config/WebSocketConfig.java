package com.playground.distributed.config;

import com.playground.distributed.case3.WebSocketChatHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring 웹소켓 설정 클래스.
 * - TextWebSocketHandler 구현체를 특정 URL 경로(/case3/ws/chat)에 매핑
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketChatHandler chatHandler;

    public WebSocketConfig(WebSocketChatHandler chatHandler) {
        this.chatHandler = chatHandler;
    }

    /** 웹소켓 핸들러 등록 및 CORS(* 전체 허용) 설정 */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/case3/ws/chat").setAllowedOrigins("*");
    }
}
