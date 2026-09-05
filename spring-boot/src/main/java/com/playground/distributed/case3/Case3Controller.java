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
