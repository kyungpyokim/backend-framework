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

/**
 * Case 1: 무상태 수평 확장 및 분산 락 REST 컨트롤러.
 * - 재고 초기화 및 조회
 * - 분산 락 적용 안전 구매 (/case1/purchase/safe)
 * - 락 미적용 취약 구매 (/case1/purchase/unsafe)
 * - 슬라이딩 윈도우 처리율 제한 (/case1/rate-limit)
 */
@RestController
@RequestMapping("/case1")
public class Case1Controller {

    /** 재고 초기화 요청 불변 Record DTO (Null Object 패턴 적용) */
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

    /** 구매 요청 불변 Record DTO (Null Object 패턴 적용) */
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

    /** 상품 재고 수량 초기화 */
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

    /** 현재 상품 재고 수량 조회 */
    @GetMapping("/inventory/{itemId}")
    public Map<String, Object> getInventory(@PathVariable String itemId) {
        int stock = lockService.getInventory(itemId);
        return Map.of(
                "node_id", appConfig.getNodeId(),
                "item_id", itemId,
                "stock", stock);
    }

    /**
     * [동시성 안전] Redis 분산 락 기반 구매 처리.
     * 락 획득 타임아웃 시 429 Too Many Requests 반환.
     */
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

    /**
     * [동시성 취약] 분산 락 미적용 구매 처리 (Race Condition 학습 및 비교 시연용).
     */
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

    /**
     * [처리율 제한] 슬라이딩 윈도우 알고리즘 테스트 (10초 내 5회 제한).
     */
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
