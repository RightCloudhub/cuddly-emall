package com.example.mall.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intent_id", nullable = false)
    private Long intentId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "gateway_event_id", nullable = false, unique = true, length = 128)
    private String gatewayEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> raw = new LinkedHashMap<>();

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected PaymentEvent() {}

    public PaymentEvent(
            Long intentId, String eventType, String gatewayEventId, Map<String, Object> raw) {
        this.intentId = intentId;
        this.eventType = eventType;
        this.gatewayEventId = gatewayEventId;
        if (raw != null) {
            this.raw = new LinkedHashMap<>(raw);
        }
    }

    @PrePersist
    void onCreate() {
        this.receivedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getIntentId() {
        return intentId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getGatewayEventId() {
        return gatewayEventId;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
