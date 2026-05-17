package com.example.mall.application.payment;

public class GatewayWebhookException extends RuntimeException {

    public GatewayWebhookException(String message) {
        super(message);
    }

    public GatewayWebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
