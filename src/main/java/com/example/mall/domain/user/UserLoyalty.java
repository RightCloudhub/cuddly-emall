package com.example.mall.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_loyalty")
public class UserLoyalty {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private int points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Tier tier = Tier.BRONZE;

    @Column(name = "expiring_soon", nullable = false)
    private int expiringSoon;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserLoyalty() {}

    public UserLoyalty(Long userId) {
        this.userId = userId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public Tier getTier() {
        return tier;
    }

    public void setTier(Tier tier) {
        this.tier = tier;
    }

    public int getExpiringSoon() {
        return expiringSoon;
    }

    public void setExpiringSoon(int expiringSoon) {
        this.expiringSoon = expiringSoon;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum Tier {
        BRONZE,
        SILVER,
        GOLD,
        PLATINUM
    }
}
