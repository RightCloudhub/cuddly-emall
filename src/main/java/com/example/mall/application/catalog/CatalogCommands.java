package com.example.mall.application.catalog;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application-layer input records — independent of HTTP DTOs. */
public final class CatalogCommands {

    private CatalogCommands() {}

    public record VariantInput(
            String skuCode, Map<String, String> attributes, BigDecimal price, int weightG) {
        public VariantInput {
            if (attributes == null) {
                attributes = new LinkedHashMap<>();
            }
        }
    }

    public record CreateProductCommand(
            String spuCode,
            String title,
            String description,
            Long categoryId,
            String policySnippet,
            List<VariantInput> variants) {}

    public record UpdateProductCommand(
            String title,
            String description,
            Long categoryId,
            String policySnippet,
            List<VariantInput> variants) {}
}
