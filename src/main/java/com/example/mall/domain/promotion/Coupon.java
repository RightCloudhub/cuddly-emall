package com.example.mall.domain.promotion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CouponType type;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal value;

    @Column(name = "min_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal minTotal = BigDecimal.ZERO;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Coupon() {}

    public Coupon(
            String code, CouponType type, BigDecimal value, BigDecimal minTotal, Instant expiresAt) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.minTotal = minTotal == null ? BigDecimal.ZERO : minTotal;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean isActiveAt(Instant when) {
        if (!enabled) {
            return false;
        }
        return expiresAt == null || when.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public CouponType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getMinTotal() {
        return minTotal;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
