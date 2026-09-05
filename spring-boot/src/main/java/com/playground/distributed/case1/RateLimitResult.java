package com.playground.distributed.case1;

public record RateLimitResult(boolean allowed, long remaining) {

    public static final RateLimitResult BLOCKED = new RateLimitResult(false, 0L);

    public static RateLimitResult of(boolean allowed, long remaining) {
        return new RateLimitResult(allowed, remaining);
    }
}
