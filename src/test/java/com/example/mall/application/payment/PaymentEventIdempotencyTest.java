package com.example.mall.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.application.catalog.CatalogService;
import com.example.mall.application.inventory.InventoryService;
import com.example.mall.application.order.PlaceOrderCommand;
import com.example.mall.application.order.PlaceOrderResult;
import com.example.mall.application.order.PlaceOrderService;
import com.example.mall.application.payment.PaymentService.ApplyResult;
import com.example.mall.application.user.UserRegistrationService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.order.OrderRepository;
import com.example.mall.domain.order.OrderStatus;
import com.example.mall.domain.payment.PaymentEventRepository;
import com.example.mall.domain.payment.PaymentIntentRepository;
import com.example.mall.domain.shipment.ShipmentRepository;
import com.example.mall.domain.user.Address;
import com.example.mall.domain.user.AddressRepository;
import com.example.mall.domain.user.User;
import com.example.mall.support.PostgresBackedTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Same gateway_event_id delivered twice must not double-apply state changes. */
@SpringBootTest
@TestPropertySource(properties = {"mall.payment.mock.settlement-delay-ms=600000"})
class PaymentEventIdempotencyTest extends PostgresBackedTest {

    @Autowired PlaceOrderService placeOrderService;
    @Autowired PaymentService paymentService;
    @Autowired CatalogService catalogService;
    @Autowired InventoryService inventoryService;
    @Autowired UserRegistrationService userRegistrationService;
    @Autowired AddressRepository addressRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentIntentRepository paymentIntentRepository;
    @Autowired PaymentEventRepository paymentEventRepository;
    @Autowired ShipmentRepository shipmentRepository;

    @Test
    void duplicateEventIsIgnored() {
        User user = newUser();
        Address addr = newAddress(user.getId());
        ProductVariant variant = newSku("SPU-IDEM", "SKU-IDEM", "40.00", 5);

        PlaceOrderResult placed =
                placeOrderService.place(
                        new PlaceOrderCommand(
                                user.getId(),
                                addr.getId(),
                                null,
                                List.of(new PlaceOrderCommand.LineItem(variant.getId(), 2))));
        Long orderId = placed.order().getId();
        String gatewayIntentId = placed.intent().getGatewayIntentId();

        GatewayEvent event =
                new GatewayEvent(
                        "test_evt_idempotency_1",
                        PaymentService.EVENT_PAYMENT_SUCCEEDED,
                        gatewayIntentId,
                        Map.of("amount", "80.00"));

        ApplyResult first = paymentService.applyEvent(MockPaymentGateway.NAME, event);
        assertThat(first.duplicate()).isFalse();
        assertThat(first.eventType()).isEqualTo(PaymentService.EVENT_PAYMENT_SUCCEEDED);

        // Replay the same event_id.
        ApplyResult second = paymentService.applyEvent(MockPaymentGateway.NAME, event);
        assertThat(second.duplicate()).isTrue();

        // Side effects applied exactly once.
        long eventCount = paymentEventRepository.findAll().stream()
                .filter(e -> "test_evt_idempotency_1".equals(e.getGatewayEventId()))
                .count();
        assertThat(eventCount).isEqualTo(1);

        var order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        // Inventory deducted only once: started at 5, reserved 2 on order, deducted on settle.
        var inv = inventoryService.get(variant.getId());
        assertThat(inv.getReserved()).isZero();
        assertThat(inv.getAvailable()).isEqualTo(3);

        // Exactly one shipment row.
        assertThat(shipmentRepository.findByOrderId(orderId)).isPresent();
    }

    private User newUser() {
        String suffix = String.valueOf(System.nanoTime());
        return userRegistrationService.register(
                "idem" + suffix, "idem" + suffix + "@example.com", "password123");
    }

    private Address newAddress(Long userId) {
        return addressRepository.save(
                new Address(
                        userId,
                        "Charlie",
                        "13800003333",
                        "Guangdong",
                        "Shenzhen",
                        "Nanshan",
                        "9 Idem Rd",
                        true));
    }

    private ProductVariant newSku(String spu, String sku, String price, int stock) {
        Product product =
                catalogService.createDraft(
                        new CreateProductCommand(
                                spu,
                                "Idem " + spu,
                                "",
                                null,
                                "",
                                List.of(
                                        new VariantInput(
                                                sku,
                                                Map.of(),
                                                new BigDecimal(price),
                                                0))));
        ProductVariant v = catalogService.variantsOf(product.getId()).get(0);
        inventoryService.restock(v.getId(), stock);
        return v;
    }
}
