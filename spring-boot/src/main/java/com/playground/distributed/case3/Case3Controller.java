package com.playground.distributed.case3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playground.distributed.config.AppConfig;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 3: 실시간 분산 브로드캐스트 REST 컨트롤러.
 * - HTTP POST 호출을 통해 특정 방(room)의 웹소켓 클라이언트들에게 외부 브로드캐스트
 */
@RestController
@RequestMapping("/case3")
public class Case3Controller {

    /** 브로드캐스트 요청 불변 Record DTO (Null Object 패턴 적용) */
    public record BroadcastRequest(
            @JsonProperty("room_id") String roomId,
            @JsonProperty("sender") String sender,
            @JsonProperty("content") String content) {
        public static final BroadcastRequest DEFAULT =
                new BroadcastRequest("general", "anonymous", "");

        public BroadcastRequest {
            roomId = (roomId != null && !roomId.isBlank()) ? roomId : "general";
            sender = (sender != null && !sender.isBlank()) ? sender : "anonymous";
            content = content != null ? content : "";
        }

        public static BroadcastRequest ofNullable(BroadcastRequest req) {
            return req != null ? req : DEFAULT;
        }
    }

    private final RedisPubSubService pubSubService;
    private final AppConfig appConfig;

    public Case3Controller(RedisPubSubService pubSubService, AppConfig appConfig) {
        this.pubSubService = pubSubService;
        this.appConfig = appConfig;
    }

    /**
     * [HTTP 기반 브로드캐스트]
     * 외부 시스템에서 HTTP 호출로 특정 방에 메시지를 발행하면 Redis Pub/Sub을 거쳐 모든 인스턴스의 웹소켓으로 전파됩니다.
     */
    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@RequestBody(required = false) BroadcastRequest req) {
        var request = BroadcastRequest.ofNullable(req);

        String msg =
                String.format(
                        "{\"type\":\"broadcast\",\"sender\":\"%s\",\"content\":\"%s\",\"origin_node\":\"%s\"}",
                        request.sender(), request.content(), appConfig.getNodeId());

        pubSubService.publish(request.roomId(), msg);

        return Map.of(
                "status",
                "published",
                "room_id",
                request.roomId(),
                "origin_node",
                appConfig.getNodeId());
    }
}
