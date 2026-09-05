package com.playground.distributed.case1;

import com.playground.distributed.config.AppConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/case1")
public class Case1Controller {

    private final DistributedLockService lockService;
    private final RateLimiterService rateLimiterService;
    private final AppConfig appConfig;

    public Case1Controller(DistributedLockService lockService, RateLimiterService rateLimiterService, AppConfig appConfig) {
        this.lockService = lockService;
        this.rateLimiterService = rateLimiterService;
        this.appConfig = appConfig;
    }

    @PostMapping("/inventory/init")
    public Map<String, Object> initInventory(@RequestBody Map<String, Object> body) {
        String itemId = (String) body.get("item_id");
        int stock = ((Number) body.get("stock")).intValue();
        lockService.initInventory(itemId, stock);
        return Map.of(
            "node_id", appConfig.getNodeId(),
            "item_id", itemId,
            "stock", stock,
            "message", "Inventory initialized"
        );
    }

    @GetMapping("/inventory/{itemId}")
    public Map<String, Object> getInventory(@PathVariable String itemId) {
        int stock = lockService.getInventory(itemId);
        return Map.of(
            "node_id", appConfig.getNodeId(),
            "item_id", itemId,
            "stock", stock
        );
    }

    @PostMapping("/purchase/safe")
    public ResponseEntity<Map<String, Object>> purchaseSafe(@RequestBody Map<String, Object> body) {
        String itemId = (String) body.get("item_id");
        int quantity = body.containsKey("quantity") ? ((Number) body.get("quantity")).intValue() : 1;

        try {
            Map<String, Object> res = lockService.purchaseWithLock(itemId, quantity);
            return ResponseEntity.ok(Map.of(
                "node_id", appConfig.getNodeId(),
                "success", res.get("success"),
                "message", res.get("message"),
                "remaining_stock", res.get("remaining")
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("message", "Server busy, please retry in a moment"));
        }
    }

    @PostMapping("/purchase/unsafe")
    public ResponseEntity<Map<String, Object>> purchaseUnsafe(@RequestBody Map<String, Object> body) {
        String itemId = (String) body.get("item_id");
        int quantity = body.containsKey("quantity") ? ((Number) body.get("quantity")).intValue() : 1;

        Map<String, Object> res = lockService.purchaseWithoutLock(itemId, quantity);
        return ResponseEntity.ok(Map.of(
            "node_id", appConfig.getNodeId(),
            "success", res.get("success"),
            "message", res.get("message"),
            "remaining_stock", res.get("remaining")
        ));
    }

    @GetMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> checkRateLimit(@RequestParam(defaultValue = "client-default") String client_id) {
        Map<String, Object> res = rateLimiterService.checkLimit(client_id, 5, 10);
        boolean allowed = (Boolean) res.get("allowed");
        long remaining = (Long) res.get("remaining");

        if (!allowed) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("detail", "Rate limit exceeded. Remaining: " + remaining));
        }

        return ResponseEntity.ok(Map.of(
            "node_id", appConfig.getNodeId(),
            "client_id", client_id,
            "allowed", allowed,
            "remaining_requests", remaining
        ));
    }
}
