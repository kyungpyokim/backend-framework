package com.playground.distributed.case1;

/**
 * 처리율 제한 검사 결과 불변 Record.
 *
 * @param allowed 요청 허용 여부
 * @param remaining 윈도우 내 남은 요청 허용 횟수
 */
public record RateLimitResult(boolean allowed, long remaining) {

    /** 차단 시 반환할 Sentinel 상수 */
    public static final RateLimitResult BLOCKED = new RateLimitResult(false, 0L);

    /** 결과 생성 팩토리 메서드 */
    public static RateLimitResult of(boolean allowed, long remaining) {
        return new RateLimitResult(allowed, remaining);
    }
}
