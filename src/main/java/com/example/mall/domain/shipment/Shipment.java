package com.example.mall.domain.shipment;

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
import java.time.Instant;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "tracking_no", nullable = false, unique = true, length = 64)
    private String trackingNo;

    @Column(nullable = false, length = 32)
    private String carrier = "SF";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "estimated_delivery", nullable = false, length = 64)
    private String estimatedDelivery = "2-3 business days";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected Shipment() {}

    public Shipment(Long orderId, String trackingNo, String carrier) {
        this.orderId = orderId;
        this.trackingNo = trackingNo;
        if (carrier != null && !carrier.isBlank()) {
            this.carrier = carrier;
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

    public void markShipped() {
        if (status != ShipmentStatus.PENDING) {
            throw new IllegalStateException(
                    "cannot mark shipped from status " + status + " for tracking " + trackingNo);
        }
        this.status = ShipmentStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    public void markDelivered() {
        if (status != ShipmentStatus.SHIPPED) {
            throw new IllegalStateException(
                    "cannot mark delivered from status " + status + " for tracking " + trackingNo);
        }
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public String getCarrier() {
        return carrier;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public String getEstimatedDelivery() {
        return estimatedDelivery;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
