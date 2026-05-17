package com.example.mall.application.payment;

import com.example.mall.domain.order.Order;
import com.example.mall.domain.payment.PaymentIntent;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Pluggable payment gateway. Implementations are stateless beans selected by {@link #name()};
 * {@link MockPaymentGateway} is the default for PR3 and tests.
 */
public interface PaymentGateway {

    String name();

    GatewayIntentResult createIntent(Order order, BigDecimal amount, String currency);

    /**
     * Verify a webhook payload and decode it to a {@link GatewayEvent}. Throws {@link
     * GatewayWebhookException} if the signature is missing or invalid. The transport layer is
     * responsible for body capture and header forwarding.
     */
    GatewayEvent verifyWebhook(String rawBody, Map<String, String> headers);

    /** Issue a refund. PR3 only sketches the surface; concrete gateways implement later. */
    default void refund(PaymentIntent intent, BigDecimal amount) {
        throw new UnsupportedOperationException(name() + " refund not implemented");
    }
}
