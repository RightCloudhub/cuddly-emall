package com.example.mall.web.catalog;

import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductImage;
import com.example.mall.domain.catalog.ProductVariant;
import java.util.List;

public record ProductResponse(
        Long id,
        String spuCode,
        String title,
        String description,
        Long categoryId,
        String status,
        String policySnippet,
        List<VariantResponse> variants,
        List<String> images) {

    public static ProductResponse from(
            Product p, List<ProductVariant> variants, List<ProductImage> images) {
        return new ProductResponse(
                p.getId(),
                p.getSpuCode(),
                p.getTitle(),
                p.getDescription(),
                p.getCategoryId(),
                p.getStatus().name(),
                p.getPolicySnippet(),
                variants.stream().map(VariantResponse::from).toList(),
                images.stream().map(ProductImage::getUrl).toList());
    }
}
