package com.example.mall.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.application.catalog.CatalogService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.outbox.KbOutboxEntry;
import com.example.mall.domain.outbox.KbOutboxRepository;
import com.example.mall.integration.askflow.AskFlowApiClient;
import com.example.mall.integration.askflow.AskFlowApiException;
import com.example.mall.support.PostgresBackedTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * End-to-end test of the KB outbox worker against a real Postgres. The AskFlow client is mocked so
 * we can assert exact API arguments and inject failures.
 *
 * <p>The scheduled tick is pushed far into the future ({@code kb-fixed-delay-ms=600000}) so the
 * test controls when drain happens.
 */
@SpringBootTest
@TestPropertySource(
        properties = {
            "mall.askflow.kb-fixed-delay-ms=600000",
            "mall.askflow.user-fixed-delay-ms=600000",
            "mall.askflow.service-token=test-service-token",
            "mall.askflow.kb-max-retries=2"
        })
class KbSyncWorkerIntegrationTest extends PostgresBackedTest {

    @Autowired KbSyncWorker kbSyncWorker;
    @Autowired CatalogService catalogService;
    @Autowired KbOutboxRepository outboxRepository;
    @MockBean AskFlowApiClient askFlowApiClient;

    @Test
    void publishingProductWritesOutboxAndWorkerUploadsToAskFlow() {
        Product product =
                catalogService.createDraft(
                        new CreateProductCommand(
                                "SPU-KB-1",
                                "Worker Test Product",
                                "desc",
                                null,
                                "policy here",
                                List.of(
                                        new VariantInput(
                                                "SKU-KB-1", Map.of(), new BigDecimal("12.50"), 100))));
        catalogService.publish(product.getId());

        long pendingBefore = outboxRepository.countByStatus(KbOutboxEntry.Status.pending);
        assertThat(pendingBefore).isGreaterThanOrEqualTo(1);

        int drained = kbSyncWorker.drainOnce();
        assertThat(drained).isGreaterThanOrEqualTo(1);

        verify(askFlowApiClient)
                .uploadDocument(
                        eq("mall:product:SPU-KB-1"),
                        eq("Worker Test Product"),
                        contains("SKU-KB-1"));

        // The row is now processed; pending count dropped to zero.
        assertThat(outboxRepository.countByStatus(KbOutboxEntry.Status.pending)).isZero();
    }

    @Test
    void delistedProductEmitsDelete() {
        Product product =
                catalogService.createDraft(
                        new CreateProductCommand(
                                "SPU-KB-DEL",
                                "Delisted",
                                "desc",
                                null,
                                "",
                                List.of(
                                        new VariantInput(
                                                "SKU-KB-DEL",
                                                Map.of(),
                                                new BigDecimal("9.99"),
                                                10))));
        catalogService.publish(product.getId());
        kbSyncWorker.drainOnce(); // clear initial upsert

        catalogService.delist(product.getId());
        kbSyncWorker.drainOnce();

        verify(askFlowApiClient).deleteDocument("mall:product:SPU-KB-DEL");
    }

    @Test
    void failureCausesRetryThenDeadLetter() {
        Product product =
                catalogService.createDraft(
                        new CreateProductCommand(
                                "SPU-KB-FAIL",
                                "Fails",
                                "",
                                null,
                                "",
                                List.of(
                                        new VariantInput(
                                                "SKU-KB-FAIL",
                                                Map.of(),
                                                new BigDecimal("1.00"),
                                                1))));
        catalogService.publish(product.getId());

        doThrow(new AskFlowApiException("boom", 500))
                .when(askFlowApiClient)
                .uploadDocument(any(), any(), any());

        kbSyncWorker.drainOnce();
        // retry-count 1, still pending
        var rows = outboxRepository.findByStatusOrderByIdAsc(KbOutboxEntry.Status.pending);
        assertThat(rows).anyMatch(r -> r.getRetryCount() == 1);

        kbSyncWorker.drainOnce();
        // retry-count 2 == max → dead
        var dead = outboxRepository.findByStatusOrderByIdAsc(KbOutboxEntry.Status.dead);
        assertThat(dead).anyMatch(r -> "SPU-KB-FAIL".equals(r.getResourceId()));
    }
}
