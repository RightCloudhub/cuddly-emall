package com.example.mall.application.payment;

import java.util.Map;

/** A verified webhook event from a {@link PaymentGateway}. */
public record GatewayEvent(
        String gatewayEventId,
        String eventType,
        String gatewayIntentId,
        Map<String, Object> raw) {}
