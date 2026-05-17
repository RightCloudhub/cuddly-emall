package com.example.mall.domain.inventory;

public class InsufficientStockException extends RuntimeException {
    private final Long skuId;
    private final int available;
    private final int requested;

    public InsufficientStockException(Long skuId, int available, int requested) {
        super("insufficient stock for sku " + skuId + ": available=" + available + " requested=" + requested);
        this.skuId = skuId;
        this.available = available;
        this.requested = requested;
    }

    public Long getSkuId() {
        return skuId;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}
