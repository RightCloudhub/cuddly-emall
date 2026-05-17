package com.example.mall.domain.payment;

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

@Entity
@Table(name = "payment_intents")
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 32)
    private String gateway;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentIntentStatus status = PaymentIntentStatus.REQUIRES_ACTION;

    @Column(name = "gateway_intent_id", unique = true, length = 128)
    private String gatewayIntentId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "CNY";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentIntent() {}

    public PaymentIntent(Long orderId, String gateway, BigDecimal amount, String currency) {
        this.orderId = orderId;
        this.gateway = gateway;
        this.amount = amount;
        if (currency != null) {
            this.currency = currency;
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

    public void attachGatewayId(String gatewayIntentId) {
        this.gatewayIntentId = gatewayIntentId;
    }

    public void markStatus(PaymentIntentStatus next) {
        this.status = next;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getGateway() {
        return gateway;
    }

    public PaymentIntentStatus getStatus() {
        return status;
    }

    public String getGatewayIntentId() {
        return gatewayIntentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
