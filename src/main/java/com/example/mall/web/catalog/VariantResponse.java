package com.example.mall.web.catalog;

import com.example.mall.domain.catalog.ProductVariant;
import java.math.BigDecimal;
import java.util.Map;

public record VariantResponse(
        Long id, String skuCode, Map<String, String> attributes, BigDecimal price, int weightG) {
    public static VariantResponse from(ProductVariant v) {
        return new VariantResponse(
                v.getId(), v.getSkuCode(), v.getAttributes(), v.getPrice(), v.getWeightG());
    }
}
