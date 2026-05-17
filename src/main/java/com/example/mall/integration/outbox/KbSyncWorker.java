package com.example.mall.integration.outbox;

import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductRepository;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.catalog.ProductVariantRepository;
import com.example.mall.domain.outbox.KbOutboxEntry;
import com.example.mall.domain.outbox.KbOutboxRepository;
import com.example.mall.integration.AskFlowProperties;
import com.example.mall.integration.askflow.AskFlowApiClient;
import com.example.mall.integration.askflow.AskFlowApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains {@code mall_kb_outbox} by calling AskFlow's embedding API. Runs on the scheduling pool
 * configured in {@code SchedulingConfig}.
 *
 * <p>Each tick holds a transaction long enough to {@code SELECT ... FOR UPDATE SKIP LOCKED} a batch
 * and process it. Successful rows flip to {@code processed}; failures bump retry count and on the
 * Nth failure flip to {@code dead}. The lock is released when the transaction commits — multiple
 * mall instances may run the worker concurrently and the {@code SKIP LOCKED} clause keeps them
 * from stepping on each other.
 */
@Component
public class KbSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(KbSyncWorker.class);

    private final KbOutboxRepository outboxRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final AskFlowApiClient askFlow;
    private final AskFlowProperties props;

    public KbSyncWorker(
            KbOutboxRepository outboxRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            AskFlowApiClient askFlow,
            AskFlowProperties props) {
        this.outboxRepository = outboxRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.askFlow = askFlow;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${mall.askflow.kb-fixed-delay-ms:10000}")
    public void tick() {
        try {
            int processed = drainOnce();
            if (processed > 0) {
                log.debug("kb-sync drained {} rows", processed);
            }
        } catch (RuntimeException e) {
            log.warn("kb-sync tick failed: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int drainOnce() {
        List<KbOutboxEntry> batch = outboxRepository.lockNextBatch(props.getBatchSize());
        for (KbOutboxEntry row : batch) {
            try {
                process(row);
                row.markProcessed();
            } catch (RuntimeException e) {
                row.recordFailure(truncate(e.getMessage()), props.getKbMaxRetries());
                if (row.getStatus() == KbOutboxEntry.Status.dead) {
                    log.error(
                            "kb-sync row {} (resource={}:{}) moved to dead after {} retries: {}",
                            row.getId(),
                            row.getResourceType(),
                            row.getResourceId(),
                            row.getRetryCount(),
                            e.getMessage());
                } else {
                    log.warn(
                            "kb-sync row {} failed (retry {}): {}",
                            row.getId(),
                            row.getRetryCount(),
                            e.getMessage());
                }
            }
        }
        return batch.size();
    }

    private void process(KbOutboxEntry row) {
        if (!"product".equals(row.getResourceType())) {
            // policy/faq path: payload carries the rendered markdown + title directly.
            String docId = row.getResourceType() + ":" + row.getResourceId();
            String fullId = "mall:" + docId;
            if (row.getOp() == KbOutboxEntry.Op.delete) {
                askFlow.deleteDocument(fullId);
                return;
            }
            String title = String.valueOf(row.getPayload().getOrDefault("title", row.getResourceId()));
            String markdown = String.valueOf(row.getPayload().getOrDefault("markdown", ""));
            askFlow.uploadDocument(fullId, title, markdown);
            return;
        }

        String spuCode = row.getResourceId();
        String docId = CatalogKbRenderer.productDocId(spuCode);
        if (row.getOp() == KbOutboxEntry.Op.delete) {
            askFlow.deleteDocument(docId);
            return;
        }
        // Re-read the product to render *current* state — payload is best-effort, the DB is truth.
        Product product =
                productRepository
                        .findBySpuCode(spuCode)
                        .orElseThrow(
                                () ->
                                        new AskFlowApiException(
                                                "product disappeared before sync: " + spuCode, -1));
        List<ProductVariant> variants = variantRepository.findByProductId(product.getId());
        String markdown = CatalogKbRenderer.render(product, variants);
        askFlow.uploadDocument(docId, product.getTitle(), markdown);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
