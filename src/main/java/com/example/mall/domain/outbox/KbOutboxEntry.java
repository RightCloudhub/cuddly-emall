package com.example.mall.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Outbox row for KB synchronization. Inserted in the same transaction as a catalog/policy/FAQ
 * mutation; a scheduled worker (PR4) drains it by calling AskFlow's embedding endpoints.
 *
 * <p>Resource type/id conventions:
 * <ul>
 *   <li>{@code product} / {@code sku-code} → AskFlow source {@code mall:product:{sku}}
 *   <li>{@code policy} / {@code slug}      → {@code mall:policy:{slug}}
 *   <li>{@code faq}    / {@code slug}      → {@code mall:faq:{slug}}
 * </ul>
 */
@Entity
@Table(name = "mall_kb_outbox")
public class KbOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Op op;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 128)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.pending;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected KbOutboxEntry() {}

    public KbOutboxEntry(
            Op op, String resourceType, String resourceId, Map<String, Object> payload) {
        this.op = op;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        if (payload != null) {
            this.payload = new LinkedHashMap<>(payload);
        }
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Op getOp() {
        return op;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void markProcessed() {
        this.status = Status.processed;
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error, int maxRetries) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= maxRetries) {
            this.status = Status.dead;
        }
    }

    public enum Op {
        upsert,
        delete
    }

    public enum Status {
        pending,
        processed,
        dead
    }
}
