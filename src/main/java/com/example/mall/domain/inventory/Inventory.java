package com.example.mall.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Inventory per SKU. Concurrency is protected with a Postgres row lock acquired via {@code SELECT
 * ... FOR UPDATE} in the repository; the {@code @Version} field provides an additional cheap
 * safety net for retries.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "sku_id")
    private Long skuId;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Inventory() {}

    public Inventory(Long skuId, int available) {
        this.skuId = skuId;
        this.available = available;
        this.reserved = 0;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void reserve(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (qty > available) {
            throw new InsufficientStockException(skuId, available, qty);
        }
        available -= qty;
        reserved += qty;
    }

    public void releaseReservation(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (qty > reserved) {
            throw new IllegalStateException("cannot release more than reserved");
        }
        reserved -= qty;
        available += qty;
    }

    public void deductReserved(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (qty > reserved) {
            throw new IllegalStateException("cannot deduct more than reserved");
        }
        reserved -= qty;
    }

    public void restock(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        available += qty;
    }

    public Long getSkuId() {
        return skuId;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
