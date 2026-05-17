package com.example.mall.application.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.UpdateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.outbox.KbOutboxEntry;
import com.example.mall.domain.outbox.KbOutboxRepository;
import com.example.mall.support.PostgresBackedTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CatalogServiceIntegrationTest extends PostgresBackedTest {

    @Autowired CatalogService catalogService;
    @Autowired KbOutboxRepository outboxRepository;

    @Test
    void publishingProductEmitsOutboxUpsert() {
        long initialPending = outboxRepository.countByStatus(KbOutboxEntry.Status.pending);

        Product draft =
                catalogService.createDraft(
                        new CreateProductCommand(
                                "SPU-PUB-1",
                                "Test product",
                                "A description.",
                                null,
                                "30 day returns",
                                List.of(
                                        new VariantInput(
                                                "SKU-PUB-1",
                                                Map.of("color", "red", "size", "M"),
                                                new BigDecimal("19.9900"),
                                                250))));

        // Drafting does not emit outbox rows.
        assertThat(outboxRepository.countByStatus(KbOutboxEntry.Status.pending))
                .isEqualTo(initialPending);

        catalogService.publish(draft.getId());

        List<KbOutboxEntry> pending =
                outboxRepository.findByStatusOrderByIdAsc(KbOutboxEntry.Status.pending);
        assertThat(pending).hasSize((int) (initialPending + 1));
        KbOutboxEntry latest = pending.get(pending.size() - 1);
        assertThat(latest.getOp()).isEqualTo(KbOutboxEntry.Op.upsert);
        assertThat(latest.getResourceType()).isEqualTo("product");
        assertThat(latest.getResourceId()).isEqualTo("SPU-PUB-1");
        assertThat(latest.getPayload()).containsEntry("title", "Test product");
    }

    @Test
    void delistingPublishedProductEmitsOutboxDelete() {
        Product draft =
                catalogService.createDraft(
                        new CreateProductCommand(
                                "SPU-DEL-1",
                                "Delist me",
                                "",
                                null,
                                "",
                                List.of(
                                        new VariantInput(
                                                "SKU-DEL-1",
                                                Map.of(),
                                                new BigDecimal("1.0000"),
                                                0))));
        catalogService.publish(draft.getId());

        catalogService.delist(draft.getId());

        List<KbOutboxEntry> pending =
                outboxRepository.findByStatusOrderByIdAsc(KbOutboxEntry.Status.pending);
        KbOutboxEntry latest = pending.get(pending.size() - 1);
        assertThat(latest.getOp()).isEqualTo(KbOutboxEntry.Op.delete);
        assertThat(latest.getResourceId()).isEqualTo("SPU-DEL-1");
    }

    @Test
    void updatingDraftProductDoesNotEmitOutbox() {
        Product draft =
                catalogService.createDraft(
                        new CreateProductCommand(
                                "SPU-UPD-1",
                                "Initial",
                                "",
                                null,
                                "",
                                List.of(
                                        new VariantInput(
                                                "SKU-UPD-1",
                                                Map.of(),
                                                new BigDecimal("5.0000"),
                                                0))));
        long before = outboxRepository.countByStatus(KbOutboxEntry.Status.pending);

        catalogService.update(
                draft.getId(),
                new UpdateProductCommand(
                        "New title",
                        "new desc",
                        null,
                        "",
                        List.of(
                                new VariantInput(
                                        "SKU-UPD-1",
                                        Map.of("flavor", "vanilla"),
                                        new BigDecimal("6.0000"),
                                        10))));

        assertThat(outboxRepository.countByStatus(KbOutboxEntry.Status.pending)).isEqualTo(before);
    }

    @Test
    void updatingPublishedProductReemitsUpsert() {
        Product draft =
                catalogService.createDraft(
                        new CreateProductCommand(
                                "SPU-REUP-1",
                                "Pub Initial",
                                "",
                                null,
                                "",
                                List.of(
                                        new VariantInput(
                                                "SKU-REUP-1",
                                                Map.of(),
                                                new BigDecimal("5.0000"),
                                                0))));
        catalogService.publish(draft.getId());
        long before = outboxRepository.countByStatus(KbOutboxEntry.Status.pending);

        catalogService.update(
                draft.getId(),
                new UpdateProductCommand(
                        "Updated title",
                        "updated desc",
                        null,
                        "",
                        List.of(
                                new VariantInput(
                                        "SKU-REUP-1",
                                        Map.of(),
                                        new BigDecimal("6.0000"),
                                        10))));

        assertThat(outboxRepository.countByStatus(KbOutboxEntry.Status.pending))
                .isEqualTo(before + 1);
    }
}
