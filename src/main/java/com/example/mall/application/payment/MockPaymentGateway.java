package com.example.mall.application.payment;

import com.example.mall.domain.order.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Default gateway for PR3 and tests. {@link #createIntent} returns a synthetic intent id, and the
 * paired success event is delivered to {@link PaymentService} {@code mockSettlementDelay} later
 * (default 3s) — simulating a real gateway's async confirmation.
 *
 * <p>For tests that want determinism set {@code mall.payment.mock.settlement-delay-ms=0}; the
 * scheduler then fires the success on the next tick instead of after 3s.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    public static final String NAME = "MOCK";

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final TaskScheduler scheduler;
    private final PaymentService paymentService;
    private final long settlementDelayMs;

    /** Captures the latest scheduled-event for tests that want to assert without sleeping. */
    private final AtomicReference<GatewayEvent> lastScheduledEvent = new AtomicReference<>();

    public MockPaymentGateway(
            TaskScheduler scheduler,
            @Lazy PaymentService paymentService,
            @Value("${mall.payment.mock.settlement-delay-ms:3000}") long settlementDelayMs) {
        this.scheduler = scheduler;
        this.paymentService = paymentService;
        this.settlementDelayMs = settlementDelayMs;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public GatewayIntentResult createIntent(Order order, BigDecimal amount, String currency) {
        String gatewayIntentId = "mock_pi_" + UUID.randomUUID();
        String gatewayEventId = "mock_evt_" + UUID.randomUUID();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", gatewayIntentId);
        raw.put("amount", amount.toPlainString());
        raw.put("currency", currency);
        raw.put("order_no", order.getOrderNo());

        GatewayEvent settlement =
                new GatewayEvent(
                        gatewayEventId, "payment_succeeded", gatewayIntentId, Map.copyOf(raw));
        lastScheduledEvent.set(settlement);

        scheduler.schedule(
                () -> {
                    try {
                        paymentService.applyEvent(NAME, settlement);
                    } catch (Exception ex) {
                        log.error(
                                "mock settlement failed for intent {}: {}",
                                gatewayIntentId,
                                ex.getMessage(),
                                ex);
                    }
                },
                Instant.now().plusMillis(Math.max(0, settlementDelayMs)));

        return new GatewayIntentResult(gatewayIntentId, "REQUIRES_ACTION", raw);
    }

    @Override
    public GatewayEvent verifyWebhook(String rawBody, Map<String, String> headers) {
        // Mock has no signature — accept a plain body of the shape
        // {"id":"mock_evt_x","type":"payment_succeeded","intent_id":"mock_pi_y"}
        if (rawBody == null || rawBody.isBlank()) {
            throw new GatewayWebhookException("empty body");
        }
        // Tests can post via the controller using the most recent scheduled event.
        // No JSON parsing here to keep PR3 small: parsing lives in PaymentWebhookController.
        throw new UnsupportedOperationException(
                "MockPaymentGateway.verifyWebhook is not used; mock delivers events via TaskScheduler");
    }

    /** Visible for tests: the synthetic event scheduled for the most recent intent. */
    public GatewayEvent lastScheduledEvent() {
        return lastScheduledEvent.get();
    }
}
