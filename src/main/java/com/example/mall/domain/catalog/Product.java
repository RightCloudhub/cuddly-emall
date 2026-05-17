package com.example.mall.domain.catalog;

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
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spu_code", nullable = false, unique = true, length = 64)
    private String spuCode;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description = "";

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Column(name = "policy_snippet", nullable = false, columnDefinition = "text")
    private String policySnippet = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(String spuCode, String title, String description, Long categoryId) {
        this.spuCode = spuCode;
        this.title = title;
        this.description = description == null ? "" : description;
        this.categoryId = categoryId;
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

    public Long getId() {
        return id;
    }

    public String getSpuCode() {
        return spuCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Status getStatus() {
        return status;
    }

    public void publish() {
        this.status = Status.PUBLISHED;
    }

    public void delist() {
        this.status = Status.DELISTED;
    }

    public String getPolicySnippet() {
        return policySnippet;
    }

    public void setPolicySnippet(String policySnippet) {
        this.policySnippet = policySnippet == null ? "" : policySnippet;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum Status {
        DRAFT,
        PUBLISHED,
        DELISTED
    }
}
