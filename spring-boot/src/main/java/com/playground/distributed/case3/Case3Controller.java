package com.playground.distributed.case3;

import com.playground.distributed.config.AppConfig;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case3")
public class Case3Controller {

    private final RedisPubSubService pubSubService;
    private final AppConfig appConfig;

    public Case3Controller(RedisPubSubService pubSubService, AppConfig appConfig) {
        this.pubSubService = pubSubService;
        this.appConfig = appConfig;
    }

    @PostMapping("/broadcast")
    public Map<String, Object> broadcast(@RequestBody Map<String, String> body) {
        String roomId = body.get("room_id");
        String sender = body.get("sender");
        String content = body.get("content");

        String msg =
                String.format(
                        "{\"type\":\"broadcast\",\"sender\":\"%s\",\"content\":\"%s\",\"origin_node\":\"%s\"}",
                        sender, content, appConfig.getNodeId());

        pubSubService.publish(roomId, msg);

        return Map.of(
                "status", "published", "room_id", roomId, "origin_node", appConfig.getNodeId());
    }
}
