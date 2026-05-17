package com.example.mall.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 16)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal total;

    @Column(nullable = false, length = 8)
    private String currency = "CNY";

    @Column(name = "coupon_id")
    private Long couponId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> shippingAddressSnapshot = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {}

    public Order(
            String orderNo,
            Long userId,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            Long couponId,
            Map<String, Object> shippingAddressSnapshot) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.subtotal = subtotal;
        this.discount = discount == null ? BigDecimal.ZERO : discount;
        this.total = total;
        this.couponId = couponId;
        if (shippingAddressSnapshot != null) {
            this.shippingAddressSnapshot = new LinkedHashMap<>(shippingAddressSnapshot);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markPaid() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                    "cannot mark paid: current status " + status + " for order " + orderNo);
        }
        this.status = OrderStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void markShipped() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException(
                    "cannot mark shipped: current status " + status + " for order " + orderNo);
        }
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    public void markCompleted() {
        if (status != OrderStatus.SHIPPED) {
            throw new IllegalStateException(
                    "cannot mark completed: current status " + status + " for order " + orderNo);
        }
        this.status = OrderStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        if (status == OrderStatus.COMPLETED || status == OrderStatus.REFUNDED) {
            throw new IllegalStateException(
                    "cannot cancel: current status " + status + " for order " + orderNo);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getCouponId() {
        return couponId;
    }

    public Map<String, Object> getShippingAddressSnapshot() {
        return shippingAddressSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
