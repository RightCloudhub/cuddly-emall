package com.example.mall.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_code", nullable = false, unique = true, length = 64)
    private String skuCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> attributes = new LinkedHashMap<>();

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "weight_g", nullable = false)
    private int weightG;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductVariant() {}

    public ProductVariant(
            Long productId,
            String skuCode,
            Map<String, String> attributes,
            BigDecimal price,
            int weightG) {
        this.productId = productId;
        this.skuCode = skuCode;
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        this.price = price;
        this.weightG = weightG;
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

    public Long getProductId() {
        return productId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getWeightG() {
        return weightG;
    }

    public void setWeightG(int weightG) {
        this.weightG = weightG;
    }
}
