package com.example.mall.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductRepository;
import com.example.mall.domain.catalog.ProductVariantRepository;
import com.example.mall.domain.inventory.InventoryRepository;
import com.example.mall.domain.outbox.KbOutboxEntry;
import com.example.mall.domain.outbox.KbOutboxRepository;
import com.example.mall.web.error.ConflictException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceUnitTest {

    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock KbOutboxRepository outboxRepository;

    CatalogService service;

    @BeforeEach
    void setUp() {
        service =
                new CatalogService(
                        productRepository, variantRepository, inventoryRepository, outboxRepository);
    }

    @Test
    void createDraftRejectsDuplicateSpu() {
        Mockito.when(productRepository.existsBySpuCode("SPU-DUP")).thenReturn(true);

        var cmd =
                new CreateProductCommand(
                        "SPU-DUP",
                        "x",
                        "",
                        null,
                        "",
                        List.of(new VariantInput("SKU-1", Map.of(), new BigDecimal("1.0"), 0)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createDraft(cmd))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void delistOfDraftProductDoesNotEmitOutbox() {
        Product product = new Product("SPU-X", "t", "", null);
        setId(product, 1L);
        // status is DRAFT by default
        Mockito.when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(product));

        service.delist(1L);

        Mockito.verify(outboxRepository, Mockito.never()).save(Mockito.any(KbOutboxEntry.class));
        assertThat(product.getStatus()).isEqualTo(Product.Status.DELISTED);
    }

    @Test
    void publishEmitsUpsertWithVariants() {
        Product product = new Product("SPU-PUB", "t", "", null);
        setId(product, 5L);
        Mockito.when(productRepository.findById(5L)).thenReturn(java.util.Optional.of(product));
        Mockito.when(variantRepository.findByProductId(5L)).thenReturn(List.of());

        service.publish(5L);

        ArgumentCaptor<KbOutboxEntry> captor = ArgumentCaptor.forClass(KbOutboxEntry.class);
        Mockito.verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getOp()).isEqualTo(KbOutboxEntry.Op.upsert);
        assertThat(captor.getValue().getResourceId()).isEqualTo("SPU-PUB");
        assertThat(product.getStatus()).isEqualTo(Product.Status.PUBLISHED);
    }

    private static void setId(Product product, Long id) {
        try {
            var f = Product.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(product, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
