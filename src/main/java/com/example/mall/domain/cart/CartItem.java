package com.example.mall.domain.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "sku_id"}))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(nullable = false)
    private int qty;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected CartItem() {}

    public CartItem(Long cartId, Long skuId, int qty) {
        this.cartId = cartId;
        this.skuId = skuId;
        this.qty = qty;
    }

    @PrePersist
    void onCreate() {
        this.addedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCartId() {
        return cartId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        this.qty = qty;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
