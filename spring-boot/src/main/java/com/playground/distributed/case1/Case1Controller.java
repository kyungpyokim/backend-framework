package com.playground.distributed.case1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playground.distributed.config.AppConfig;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case1")
public class Case1Controller {

    public record InventoryInitRequest(
            @JsonProperty("item_id") String itemId, @JsonProperty("stock") Integer stock) {
        public String resolveItemId() {
            return (itemId != null && !itemId.isBlank()) ? itemId : "item-1";
        }

        public int resolveStock() {
            return (stock != null && stock >= 0) ? stock : 10;
        }
    }

    public record PurchaseRequest(
            @JsonProperty("item_id") String itemId, @JsonProperty("quantity") Integer quantity) {
        public String resolveItemId() {
            return (itemId != null && !itemId.isBlank()) ? itemId : "item-1";
        }

        public int resolveQuantity() {
            return (quantity != null && quantity > 0) ? quantity : 1;
        }
    }

    private final DistributedLockService lockService;
    private final RateLimiterService rateLimiterService;
    private final AppConfig appConfig;

    public Case1Controller(
            DistributedLockService lockService,
            RateLimiterService rateLimiterService,
            AppConfig appConfig) {
        this.lockService = lockService;
        this.rateLimiterService = rateLimiterService;
        this.appConfig = appConfig;
    }

    @PostMapping("/inventory/init")
    public Map<String, Object> initInventory(
            @RequestBody(required = false) InventoryInitRequest req) {
        String itemId = req != null ? req.resolveItemId() : "item-1";
        int stock = req != null ? req.resolveStock() : 10;
        lockService.initInventory(itemId, stock);
        return Map.of(
                "node_id",
                appConfig.getNodeId(),
                "item_id",
                itemId,
                "stock",
                stock,
                "message",
                "Inventory initialized");
    }

    @GetMapping("/inventory/{itemId}")
    public Map<String, Object> getInventory(@PathVariable String itemId) {
        int stock = lockService.getInventory(itemId);
        return Map.of(
                "node_id", appConfig.getNodeId(),
                "item_id", itemId,
                "stock", stock);
    }

    @PostMapping("/purchase/safe")
    public ResponseEntity<Map<String, Object>> purchaseSafe(
            @RequestBody(required = false) PurchaseRequest req) {
        String itemId = req != null ? req.resolveItemId() : "item-1";
        int quantity = req != null ? req.resolveQuantity() : 1;

        try {
            Map<String, Object> res = lockService.purchaseWithLock(itemId, quantity);
            return ResponseEntity.ok(
                    Map.of(
                            "node_id", appConfig.getNodeId(),
                            "success", Objects.requireNonNullElse(res.get("success"), false),
                            "message", Objects.requireNonNullElse(res.get("message"), ""),
                            "remaining_stock",
                                    Objects.requireNonNullElse(res.get("remaining"), 0)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Server busy, please retry in a moment"));
        }
    }

    @PostMapping("/purchase/unsafe")
    public ResponseEntity<Map<String, Object>> purchaseUnsafe(
            @RequestBody(required = false) PurchaseRequest req) {
        String itemId = req != null ? req.resolveItemId() : "item-1";
        int quantity = req != null ? req.resolveQuantity() : 1;

        Map<String, Object> res = lockService.purchaseWithoutLock(itemId, quantity);
        return ResponseEntity.ok(
                Map.of(
                        "node_id", appConfig.getNodeId(),
                        "success", Objects.requireNonNullElse(res.get("success"), false),
                        "message", Objects.requireNonNullElse(res.get("message"), ""),
                        "remaining_stock", Objects.requireNonNullElse(res.get("remaining"), 0)));
    }

    @GetMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> checkRateLimit(
            @RequestParam(defaultValue = "client-default") String client_id) {
        Map<String, Object> res = rateLimiterService.checkLimit(client_id, 5, 10);
        boolean allowed = Boolean.TRUE.equals(res.get("allowed"));
        long remaining = res.get("remaining") instanceof Number n ? n.longValue() : 0L;

        if (!allowed) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("detail", "Rate limit exceeded. Remaining: " + remaining));
        }

        return ResponseEntity.ok(
                Map.of(
                        "node_id", appConfig.getNodeId(),
                        "client_id", client_id,
                        "allowed", allowed,
                        "remaining_requests", remaining));
    }
}
