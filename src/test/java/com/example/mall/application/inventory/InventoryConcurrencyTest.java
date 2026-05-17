package com.example.mall.application.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.application.catalog.CatalogService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.inventory.InsufficientStockException;
import com.example.mall.support.PostgresBackedTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InventoryConcurrencyTest extends PostgresBackedTest {

    @Autowired CatalogService catalogService;
    @Autowired InventoryService inventoryService;

    @Test
    void concurrentReservationsCannotOversell() throws Exception {
        Product product =
                catalogService.createDraft(
                        new CreateProductCommand(
                                "SPU-LOCK-1",
                                "Stock test",
                                "",
                                null,
                                "",
                                List.of(
                                        new VariantInput(
                                                "SKU-LOCK-1",
                                                Map.of(),
                                                new BigDecimal("10.0000"),
                                                0))));
        ProductVariant variant = catalogService.variantsOf(product.getId()).get(0);
        Long skuId = variant.getId();
        inventoryService.restock(skuId, 10);

        int threads = 8;
        int perThread = 2; // total demand = 16, supply = 10 → 5 successes
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();

        Future<?>[] futures = new Future[threads];
        for (int i = 0; i < threads; i++) {
            futures[i] =
                    pool.submit(
                            () -> {
                                try {
                                    start.await();
                                    inventoryService.reserve(skuId, perThread);
                                    successes.incrementAndGet();
                                } catch (InsufficientStockException ex) {
                                    insufficient.incrementAndGet();
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            });
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(5);
        assertThat(insufficient.get()).isEqualTo(threads - 5);
        var inv = inventoryService.get(skuId);
        assertThat(inv.getAvailable()).isZero();
        assertThat(inv.getReserved()).isEqualTo(10);
    }
}
