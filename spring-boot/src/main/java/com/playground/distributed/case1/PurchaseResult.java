package com.playground.distributed.case1;

/**
 * 구매 처리 결과 불변 Record (Null Object / Sentinel Object 패턴 적용).
 *
 * @param success 구매 성공 여부
 * @param message 결과 메시지
 * @param remainingStock 구매 후 남은 재고 수량
 */
public record PurchaseResult(boolean success, String message, int remainingStock) {

    /** 품절 Sentinel 상수 */
    public static final PurchaseResult OUT_OF_STOCK = new PurchaseResult(false, "Out of stock", 0);

    /** 락 획득 타임아웃 Sentinel 상수 */
    public static final PurchaseResult TIMEOUT =
            new PurchaseResult(false, "Server busy, please retry in a moment", 0);

    /** 구매 성공 팩토리 메서드 */
    public static PurchaseResult ok(int remainingStock) {
        return new PurchaseResult(true, "Purchase successful", remainingStock);
    }

    /** 재고 부족 팩토리 메서드 */
    public static PurchaseResult outOfStock(int currentStock) {
        return new PurchaseResult(false, "Out of stock", currentStock);
    }
}
