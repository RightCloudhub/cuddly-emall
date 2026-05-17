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
 * Outbox row written when a mall user is registered or has their profile updated. {@code
 * UserSyncWorker} (PR4) drains it by calling AskFlow's {@code POST /api/v1/admin/users} and persists
 * the resulting {@code askflow_user_id} into {@code user_id_mapping}.
 */
@Entity
@Table(name = "mall_user_outbox")
public class UserOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Op op;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    protected UserOutboxEntry() {}

    public UserOutboxEntry(Op op, Long userId, Map<String, Object> payload) {
        this.op = op;
        this.userId = userId;
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

    public Long getUserId() {
        return userId;
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
        create,
        update
    }

    public enum Status {
        pending,
        processed,
        dead
    }
}
