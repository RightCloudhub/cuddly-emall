package com.example.mall.application.payment;

import com.example.mall.domain.order.Order;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Stripe gateway skeleton — not wired as a Spring bean. Concrete integration lands in a later PR
 * once the Stripe SDK + webhook signature handling are added. Kept here to lock the contract.
 */
public class StripePaymentGateway implements PaymentGateway {

    public static final String NAME = "STRIPE";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public GatewayIntentResult createIntent(Order order, BigDecimal amount, String currency) {
        // TODO: integrate Stripe SDK (PaymentIntent.create)
        throw new UnsupportedOperationException("Stripe gateway not yet implemented");
    }

    @Override
    public GatewayEvent verifyWebhook(String rawBody, Map<String, String> headers) {
        // TODO: verify `Stripe-Signature` header with the webhook secret + parse event.
        throw new UnsupportedOperationException("Stripe webhook verification not yet implemented");
    }
}
