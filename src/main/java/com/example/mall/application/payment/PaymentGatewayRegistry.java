package com.example.mall.application.payment;

import com.example.mall.web.error.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Component;

/** Looks up a {@link PaymentGateway} by its {@code name()}. */
@Component
public class PaymentGatewayRegistry {

    private final List<PaymentGateway> gateways;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.gateways = gateways;
    }

    public PaymentGateway require(String name) {
        for (PaymentGateway g : gateways) {
            if (g.name().equalsIgnoreCase(name)) {
                return g;
            }
        }
        throw new NotFoundException("payment gateway not found: " + name);
    }
}
