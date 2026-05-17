package com.example.mall.application.payment;

import java.util.Map;

/** Result of {@link PaymentGateway#createIntent}. */
public record GatewayIntentResult(String gatewayIntentId, String status, Map<String, Object> raw) {}
