package com.playground.distributed.case1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playground.distributed.config.AppConfig;
import java.util.Map;
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

        public static final InventoryInitRequest DEFAULT = new InventoryInitRequest("item-1", 10);

        public InventoryInitRequest {
            itemId = (itemId != null && !itemId.isBlank()) ? itemId : "item-1";
            stock = (stock != null && stock >= 0) ? stock : 10;
        }

        public static InventoryInitRequest ofNullable(InventoryInitRequest req) {
            return req != null ? req : DEFAULT;
        }
    }

    public record PurchaseRequest(
            @JsonProperty("item_id") String itemId, @JsonProperty("quantity") Integer quantity) {

        public static final PurchaseRequest DEFAULT = new PurchaseRequest("item-1", 1);

        public PurchaseRequest {
            itemId = (itemId != null && !itemId.isBlank()) ? itemId : "item-1";
            quantity = (quantity != null && quantity > 0) ? quantity : 1;
        }

        public static PurchaseRequest ofNullable(PurchaseRequest req) {
            return req != null ? req : DEFAULT;
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
        var request = InventoryInitRequest.ofNullable(req);
        lockService.initInventory(request.itemId(), request.stock());
        return Map.of(
                "node_id", appConfig.getNodeId(),
                "item_id", request.itemId(),
                "stock", request.stock(),
                "message", "Inventory initialized");
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
        var request = PurchaseRequest.ofNullable(req);
        PurchaseResult res = lockService.purchaseWithLock(request.itemId(), request.quantity());

        if (!res.success() && res == PurchaseResult.TIMEOUT) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", res.message()));
        }

        return ResponseEntity.ok(
                Map.of(
                        "node_id", appConfig.getNodeId(),
                        "success", res.success(),
                        "message", res.message(),
                        "remaining_stock", res.remainingStock()));
    }

    @PostMapping("/purchase/unsafe")
    public ResponseEntity<Map<String, Object>> purchaseUnsafe(
            @RequestBody(required = false) PurchaseRequest req) {
        var request = PurchaseRequest.ofNullable(req);
        PurchaseResult res = lockService.purchaseWithoutLock(request.itemId(), request.quantity());
        return ResponseEntity.ok(
                Map.of(
                        "node_id", appConfig.getNodeId(),
                        "success", res.success(),
                        "message", res.message(),
                        "remaining_stock", res.remainingStock()));
    }

    @GetMapping("/rate-limit")
    public ResponseEntity<Map<String, Object>> checkRateLimit(
            @RequestParam(defaultValue = "client-default") String client_id) {
        RateLimitResult res = rateLimiterService.checkLimit(client_id, 5, 10);

        if (!res.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("detail", "Rate limit exceeded. Remaining: " + res.remaining()));
        }

        return ResponseEntity.ok(
                Map.of(
                        "node_id", appConfig.getNodeId(),
                        "client_id", client_id,
                        "allowed", res.allowed(),
                        "remaining_requests", res.remaining()));
    }
}
