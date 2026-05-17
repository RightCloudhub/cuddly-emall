package com.example.mall.domain.promotion;

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
        name = "user_coupons",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"}))
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected UserCoupon() {}

    public UserCoupon(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
    }

    @PrePersist
    void onCreate() {
        this.issuedAt = Instant.now();
    }

    public void markUsed(Long orderId) {
        if (usedAt != null) {
            throw new IllegalStateException("coupon already used");
        }
        this.usedAt = Instant.now();
        this.orderId = orderId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
