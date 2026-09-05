package com.playground.distributed.case1;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis 기반 분산 락 및 재고 차감 비즈니스 로직 서비스.
 * - SETNX(setIfAbsent)를 통한 상호 배제 및 만료 시간(TTL) 설정으로 데드락 방지
 * - Lua 스크립트를 통한 락 소유자 검증 후 안전한 원자적 해제
 */
@Service
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    // 락 해제 시 토큰 일치 여부를 검증하여 본인이 획득한 락만 안전하게 삭제하는 Lua 스크립트
    private static final String RELEASE_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "    return redis.call('del', KEYS[1]) "
                    + "else "
                    + "    return 0 "
                    + "end";

    private final DefaultRedisScript<Long> releaseScript;

    public DistributedLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.releaseScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA, Long.class);
    }

    /**
     * Redis SETNX(setIfAbsent)를 사용하여 분산 락 획득을 시도합니다.
     *
     * @param resource 락 대상 자원 식별자 (예: 'inventory:item-1')
     * @param ttlMs 락 유지 시간 (밀리초)
     * @param maxRetries 락 획득 실패 시 최대 재시도 횟수
     * @param retryDelayMs 재시도 간격 대기 시간 (밀리초)
     * @return 락 획득 성공 시 발급된 UUID 토큰 문자열, 실패 시 null
     */
    public String acquireLock(String resource, long ttlMs, int maxRetries, long retryDelayMs) {
        String token = UUID.randomUUID().toString();
        String lockKey = "lock:" + resource;

        for (int i = 0; i < maxRetries; i++) {
            // SET lock:resource token PX ttlMs NX (원자적 연산)
            Boolean acquired =
                    redisTemplate
                            .opsForValue()
                            .setIfAbsent(lockKey, token, ttlMs, TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(acquired)) {
                return token;
            }
            try {
                Thread.sleep(retryDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * Lua 스크립트를 실행하여 보유 토큰이 일치할 때만 락을 원자적으로 해제합니다.
     */
    public boolean releaseLock(String resource, String token) {
        String lockKey = "lock:" + resource;
        Long result =
                redisTemplate.execute(releaseScript, Collections.singletonList(lockKey), token);
        return result != null && result == 1L;
    }

    /** 상품 재고 수량 초기화 */
    public void initInventory(String itemId, int stock) {
        redisTemplate.opsForValue().set("stock:" + itemId, String.valueOf(stock));
    }

    /** 현재 상품 재고 수량 조회 */
    public int getInventory(String itemId) {
        String val = redisTemplate.opsForValue().get("stock:" + itemId);
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * [동시성 안전] Redis 분산 락을 적용한 상품 구매.
     * 락 획득 -> 재고 확인 -> 차감 -> finally에서 락 해제 흐름을 보장합니다.
     */
    public PurchaseResult purchaseWithLock(String itemId, int quantity) {
        String token = acquireLock("inventory:" + itemId, 5000, 20, 50);
        if (token == null) {
            return PurchaseResult.TIMEOUT;
        }

        try {
            int currentStock = getInventory(itemId);
            if (currentStock < quantity) {
                return PurchaseResult.outOfStock(currentStock);
            }
            // DB I/O 또는 비즈니스 검증 지연 시뮬레이션
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
            int newStock = currentStock - quantity;
            redisTemplate.opsForValue().set("stock:" + itemId, String.valueOf(newStock));
            return PurchaseResult.ok(newStock);
        } finally {
            releaseLock("inventory:" + itemId, token);
        }
    }

    /**
     * [동시성 취약] 분산 락 미적용 상품 구매 (Race Condition 교육 및 비교 데모용).
     */
    public PurchaseResult purchaseWithoutLock(String itemId, int quantity) {
        int currentStock = getInventory(itemId);
        if (currentStock < quantity) {
            return PurchaseResult.outOfStock(currentStock);
        }
        // 동시성 결함 발생 창 (Race condition window)
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
        int newStock = currentStock - quantity;
        redisTemplate.opsForValue().set("stock:" + itemId, String.valueOf(newStock));
        return PurchaseResult.ok(newStock);
    }
}
