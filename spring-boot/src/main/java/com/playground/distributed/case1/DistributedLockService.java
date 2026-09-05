package com.playground.distributed.case1;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("null")
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

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

    public String acquireLock(String resource, long ttlMs, int maxRetries, long retryDelayMs) {
        String token = UUID.randomUUID().toString();
        String lockKey = "lock:" + resource;

        for (int i = 0; i < maxRetries; i++) {
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

    public boolean releaseLock(String resource, String token) {
        String lockKey = "lock:" + resource;
        Long result =
                redisTemplate.execute(releaseScript, Collections.singletonList(lockKey), token);
        return result != null && result == 1L;
    }

    public void initInventory(String itemId, int stock) {
        redisTemplate.opsForValue().set("stock:" + itemId, String.valueOf(stock));
    }

    public int getInventory(String itemId) {
        String val = redisTemplate.opsForValue().get("stock:" + itemId);
        return val != null ? Integer.parseInt(val) : 0;
    }

    public Map<String, Object> purchaseWithLock(String itemId, int quantity) {
        String token = acquireLock("inventory:" + itemId, 5000, 20, 50);
        if (token == null) {
            throw new RuntimeException("Lock acquisition timeout");
        }

        try {
            int currentStock = getInventory(itemId);
            if (currentStock < quantity) {
                return Map.of(
                        "success", false, "message", "Out of stock", "remaining", currentStock);
            }
            // Artificial delay to simulate DB latency
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
            int newStock = currentStock - quantity;
            redisTemplate.opsForValue().set("stock:" + itemId, String.valueOf(newStock));
            return Map.of("success", true, "message", "Purchase successful", "remaining", newStock);
        } finally {
            releaseLock("inventory:" + itemId, token);
        }
    }

    public Map<String, Object> purchaseWithoutLock(String itemId, int quantity) {
        int currentStock = getInventory(itemId);
        if (currentStock < quantity) {
            return Map.of("success", false, "message", "Out of stock", "remaining", currentStock);
        }
        // Race condition window
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
        int newStock = currentStock - quantity;
        redisTemplate.opsForValue().set("stock:" + itemId, String.valueOf(newStock));
        return Map.of("success", true, "message", "Purchase successful", "remaining", newStock);
    }
}
