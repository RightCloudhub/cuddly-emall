package com.example.mall.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_id_mapping")
public class UserIdMapping {

    @Id
    @Column(name = "mall_user_id")
    private Long mallUserId;

    @Column(name = "askflow_user_id", nullable = false, unique = true)
    private UUID askflowUserId;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected UserIdMapping() {}

    public UserIdMapping(Long mallUserId, UUID askflowUserId) {
        this.mallUserId = mallUserId;
        this.askflowUserId = askflowUserId;
        this.syncedAt = Instant.now();
    }

    public Long getMallUserId() {
        return mallUserId;
    }

    public UUID getAskflowUserId() {
        return askflowUserId;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
