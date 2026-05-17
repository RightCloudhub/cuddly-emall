package com.example.mall.application.catalog;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.UpdateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductRepository;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.catalog.ProductVariantRepository;
import com.example.mall.domain.inventory.Inventory;
import com.example.mall.domain.inventory.InventoryRepository;
import com.example.mall.domain.outbox.KbOutboxEntry;
import com.example.mall.domain.outbox.KbOutboxRepository;
import com.example.mall.web.error.ConflictException;
import com.example.mall.web.error.NotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final KbOutboxRepository outboxRepository;

    public CatalogService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            InventoryRepository inventoryRepository,
            KbOutboxRepository outboxRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public Product createDraft(CreateProductCommand cmd) {
        if (productRepository.existsBySpuCode(cmd.spuCode())) {
            throw new ConflictException("spu code already in use");
        }
        for (VariantInput v : cmd.variants()) {
            if (variantRepository.existsBySkuCode(v.skuCode())) {
                throw new ConflictException("sku code already in use: " + v.skuCode());
            }
        }
        Product product = new Product(cmd.spuCode(), cmd.title(), cmd.description(), cmd.categoryId());
        product.setPolicySnippet(cmd.policySnippet());
        product = productRepository.save(product);

        for (VariantInput v : cmd.variants()) {
            ProductVariant variant =
                    new ProductVariant(
                            product.getId(),
                            v.skuCode(),
                            new LinkedHashMap<>(v.attributes()),
                            v.price(),
                            v.weightG());
            variant = variantRepository.save(variant);
            inventoryRepository.save(new Inventory(variant.getId(), 0));
        }
        return product;
    }

    @Transactional
    public Product update(Long productId, UpdateProductCommand cmd) {
        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(() -> new NotFoundException("product not found"));
        product.setTitle(cmd.title());
        product.setDescription(cmd.description());
        product.setCategoryId(cmd.categoryId());
        product.setPolicySnippet(cmd.policySnippet());

        // Reconcile variants by sku_code (keep existing rows, update attributes/price/weight).
        List<ProductVariant> existing = variantRepository.findByProductId(productId);
        Map<String, ProductVariant> existingBySku = new HashMap<>();
        for (ProductVariant v : existing) {
            existingBySku.put(v.getSkuCode(), v);
        }

        List<ProductVariant> kept = new ArrayList<>();
        for (VariantInput v : cmd.variants()) {
            ProductVariant variant = existingBySku.remove(v.skuCode());
            if (variant == null) {
                if (variantRepository.existsBySkuCode(v.skuCode())) {
                    throw new ConflictException("sku code already in use: " + v.skuCode());
                }
                variant =
                        new ProductVariant(
                                product.getId(),
                                v.skuCode(),
                                new LinkedHashMap<>(v.attributes()),
                                v.price(),
                                v.weightG());
                variant = variantRepository.save(variant);
                inventoryRepository.save(new Inventory(variant.getId(), 0));
            } else {
                variant.setAttributes(v.attributes());
                variant.setPrice(v.price());
                variant.setWeightG(v.weightG());
            }
            kept.add(variant);
        }
        // Variants removed from the command are deleted; inventory FK cascades.
        if (!existingBySku.isEmpty()) {
            variantRepository.deleteAll(existingBySku.values());
        }

        if (product.getStatus() == Product.Status.PUBLISHED) {
            // Re-emit upsert so RAG stays in sync.
            outboxRepository.save(CatalogOutboxPayload.upsertFor(product, kept));
        }
        return product;
    }

    @Transactional
    public Product publish(Long productId) {
        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(() -> new NotFoundException("product not found"));
        if (product.getStatus() == Product.Status.PUBLISHED) {
            return product;
        }
        product.publish();
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        outboxRepository.save(CatalogOutboxPayload.upsertFor(product, variants));
        return product;
    }

    @Transactional
    public Product delist(Long productId) {
        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(() -> new NotFoundException("product not found"));
        if (product.getStatus() == Product.Status.DELISTED) {
            return product;
        }
        boolean wasPublished = product.getStatus() == Product.Status.PUBLISHED;
        product.delist();
        if (wasPublished) {
            outboxRepository.save(CatalogOutboxPayload.deleteFor(product));
        }
        return product;
    }

    @Transactional(readOnly = true)
    public Product getRequired(Long productId) {
        return productRepository
                .findById(productId)
                .orElseThrow(() -> new NotFoundException("product not found"));
    }

    @Transactional(readOnly = true)
    public List<ProductVariant> variantsOf(Long productId) {
        return variantRepository.findByProductId(productId);
    }
}
