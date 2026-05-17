package com.example.mall.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(nullable = false)
    private int sort;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductImage() {}

    public ProductImage(Long productId, String url, int sort) {
        this.productId = productId;
        this.url = url;
        this.sort = sort;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getUrl() {
        return url;
    }

    public int getSort() {
        return sort;
    }
}
