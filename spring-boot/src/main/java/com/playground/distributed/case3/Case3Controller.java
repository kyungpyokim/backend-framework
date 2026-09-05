package com.playground.distributed.case3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playground.distributed.config.AppConfig;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case3")
public class Case3Controller {

    public record BroadcastRequest(
            @JsonProperty("room_id") String roomId,
            @JsonProperty("sender") String sender,
            @JsonProperty("content") String content) {
        public String resolveRoomId() {
            return (roomId != null && !roomId.isBlank()) ? roomId : "general";
        }

        public String resolveSender() {
            return (sender != null && !sender.isBlank()) ? sender : "anonymous";
        }

        public String resolveContent() {
            return content != null ? content : "";
        }
    }

    private final RedisPubSubService pubSubService;
    private final AppConfig appConfig;

    public Case3Controller(RedisPubSubService pubSubService, AppConfig appConfig) {
        this.pubSubService = pubSubService;
        this.appConfig = appConfig;
    }

    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@RequestBody(required = false) BroadcastRequest req) {
        String roomId = req != null ? req.resolveRoomId() : "general";
        String sender = req != null ? req.resolveSender() : "anonymous";
        String content = req != null ? req.resolveContent() : "";

        String msg =
                String.format(
                        "{\"type\":\"broadcast\",\"sender\":\"%s\",\"content\":\"%s\",\"origin_node\":\"%s\"}",
                        sender, content, appConfig.getNodeId());

        pubSubService.publish(roomId, msg);

        return Map.of(
                "status", "published", "room_id", roomId, "origin_node", appConfig.getNodeId());
    }
}
