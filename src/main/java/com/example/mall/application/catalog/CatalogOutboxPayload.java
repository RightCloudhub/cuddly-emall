package com.example.mall.application.catalog;

import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.outbox.KbOutboxEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the JSON payload stored in {@code mall_kb_outbox} for product changes. */
final class CatalogOutboxPayload {

    private CatalogOutboxPayload() {}

    static KbOutboxEntry upsertFor(Product product, List<ProductVariant> variants) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", product.getSpuCode());
        payload.put("title", product.getTitle());
        payload.put("description", product.getDescription());
        payload.put("policy_snippet", product.getPolicySnippet());
        payload.put("status", product.getStatus().name());
        payload.put("variants", renderVariants(variants));
        return new KbOutboxEntry(
                KbOutboxEntry.Op.upsert, "product", product.getSpuCode(), payload);
    }

    static KbOutboxEntry deleteFor(Product product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku", product.getSpuCode());
        return new KbOutboxEntry(
                KbOutboxEntry.Op.delete, "product", product.getSpuCode(), payload);
    }

    private static List<Map<String, Object>> renderVariants(List<ProductVariant> variants) {
        List<Map<String, Object>> out = new ArrayList<>(variants.size());
        for (ProductVariant v : variants) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sku", v.getSkuCode());
            entry.put("price", priceString(v.getPrice()));
            entry.put("weight_g", v.getWeightG());
            entry.put("attributes", v.getAttributes());
            out.add(entry);
        }
        return out;
    }

    private static String priceString(BigDecimal price) {
        return price == null ? "0.0000" : price.toPlainString();
    }
}
