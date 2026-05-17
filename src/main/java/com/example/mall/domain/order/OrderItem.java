package com.example.mall.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "sku_code", nullable = false, length = 64)
    private String skuCode;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int qty;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal lineTotal;

    protected OrderItem() {}

    public OrderItem(
            Long orderId,
            Long skuId,
            String skuCode,
            String title,
            BigDecimal unitPrice,
            int qty,
            BigDecimal lineTotal) {
        this.orderId = orderId;
        this.skuId = skuId;
        this.skuCode = skuCode;
        this.title = title;
        this.unitPrice = unitPrice;
        this.qty = qty;
        this.lineTotal = lineTotal;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQty() {
        return qty;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
