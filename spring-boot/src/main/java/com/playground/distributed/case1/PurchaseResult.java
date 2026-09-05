package com.playground.distributed.case1;

public record PurchaseResult(boolean success, String message, int remainingStock) {

    public static final PurchaseResult OUT_OF_STOCK = new PurchaseResult(false, "Out of stock", 0);

    public static final PurchaseResult TIMEOUT =
            new PurchaseResult(false, "Server busy, please retry in a moment", 0);

    public static PurchaseResult ok(int remainingStock) {
        return new PurchaseResult(true, "Purchase successful", remainingStock);
    }

    public static PurchaseResult outOfStock(int currentStock) {
        return new PurchaseResult(false, "Out of stock", currentStock);
    }
}
