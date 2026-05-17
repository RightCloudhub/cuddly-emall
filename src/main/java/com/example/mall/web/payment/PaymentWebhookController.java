package com.example.mall.web.payment;

import com.example.mall.application.payment.GatewayEvent;
import com.example.mall.application.payment.GatewayWebhookException;
import com.example.mall.application.payment.PaymentGateway;
import com.example.mall.application.payment.PaymentGatewayRegistry;
import com.example.mall.application.payment.PaymentService;
import com.example.mall.application.payment.PaymentService.ApplyResult;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives webhook events from external payment gateways. The transport delegates verification +
 * decoding to the gateway impl and event application to {@link PaymentService}.
 *
 * <p>This endpoint is intentionally not behind a JWT — gateways carry their own signature in
 * headers. PR4 will harden any additional auth.
 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentGatewayRegistry registry;
    private final PaymentService paymentService;

    public PaymentWebhookController(
            PaymentGatewayRegistry registry, PaymentService paymentService) {
        this.registry = registry;
        this.paymentService = paymentService;
    }

    @PostMapping("/{gateway}")
    public ResponseEntity<WebhookResponse> receive(
            @PathVariable("gateway") String gatewayName, HttpServletRequest request) {
        String body;
        try {
            body = readBody(request);
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new WebhookResponse("error", "could not read body"));
        }

        PaymentGateway gateway;
        try {
            gateway = registry.require(gatewayName);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new WebhookResponse("error", "unknown gateway"));
        }

        GatewayEvent event;
        try {
            event = gateway.verifyWebhook(body, headerMap(request));
        } catch (GatewayWebhookException ex) {
            log.warn("rejecting webhook for {}: {}", gatewayName, ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new WebhookResponse("error", ex.getMessage()));
        } catch (UnsupportedOperationException ex) {
            // Mock gateway has no HTTP path; signal that the caller should not be posting to it.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new WebhookResponse("error", "gateway does not accept webhooks"));
        }

        ApplyResult result = paymentService.applyEvent(gateway.name(), event);
        return ResponseEntity.ok(
                new WebhookResponse(
                        result.duplicate() ? "duplicate" : "applied",
                        result.eventType() == null ? "" : result.eventType()));
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(
                        new java.io.InputStreamReader(
                                request.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static Map<String, String> headerMap(HttpServletRequest request) {
        Map<String, String> out = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Collections.emptyMap();
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            out.put(name.toLowerCase(), request.getHeader(name));
        }
        return out;
    }

    public record WebhookResponse(String status, String eventType) {}
}
