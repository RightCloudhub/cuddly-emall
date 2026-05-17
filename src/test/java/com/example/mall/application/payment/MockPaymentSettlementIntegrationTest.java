package com.example.mall.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.application.catalog.CatalogService;
import com.example.mall.application.inventory.InventoryService;
import com.example.mall.application.order.PlaceOrderCommand;
import com.example.mall.application.order.PlaceOrderResult;
import com.example.mall.application.order.PlaceOrderService;
import com.example.mall.application.user.UserRegistrationService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.order.OrderRepository;
import com.example.mall.domain.order.OrderStatus;
import com.example.mall.domain.payment.PaymentIntentRepository;
import com.example.mall.domain.payment.PaymentIntentStatus;
import com.example.mall.domain.shipment.ShipmentRepository;
import com.example.mall.domain.shipment.ShipmentStatus;
import com.example.mall.domain.user.Address;
import com.example.mall.domain.user.AddressRepository;
import com.example.mall.domain.user.User;
import com.example.mall.support.PostgresBackedTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {"mall.payment.mock.settlement-delay-ms=0"})
class MockPaymentSettlementIntegrationTest extends PostgresBackedTest {

    private static final Pattern TRACKING = Pattern.compile("^SF\\d{10}$");

    @Autowired PlaceOrderService placeOrderService;
    @Autowired CatalogService catalogService;
    @Autowired InventoryService inventoryService;
    @Autowired UserRegistrationService userRegistrationService;
    @Autowired AddressRepository addressRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentIntentRepository paymentIntentRepository;
    @Autowired ShipmentRepository shipmentRepository;

    @Test
    void mockGatewayAutoSettlesOrderAndCreatesShipment() {
        User user = newUser();
        Address addr = newAddress(user.getId());
        ProductVariant variant = newSku("SPU-SET", "SKU-SET", "30.00", 5);

        PlaceOrderResult result =
                placeOrderService.place(
                        new PlaceOrderCommand(
                                user.getId(),
                                addr.getId(),
                                null,
                                List.of(new PlaceOrderCommand.LineItem(variant.getId(), 2))));
        Long orderId = result.order().getId();

        // The mock scheduler fires settlement on the next tick (delay=0).
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(
                        () -> {
                            var order = orderRepository.findById(orderId).orElseThrow();
                            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
                            var intent =
                                    paymentIntentRepository.findByOrderId(orderId).get(0);
                            assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.SUCCEEDED);
                            var shipment =
                                    shipmentRepository.findByOrderId(orderId).orElseThrow();
                            assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PENDING);
                            assertThat(TRACKING.matcher(shipment.getTrackingNo()).matches())
                                    .isTrue();
                        });

        // Inventory reserved → moved out of stock, not back to available.
        var inv = inventoryService.get(variant.getId());
        assertThat(inv.getReserved()).isZero();
        assertThat(inv.getAvailable()).isEqualTo(3);
    }

    private User newUser() {
        String suffix = String.valueOf(System.nanoTime());
        return userRegistrationService.register(
                "settle" + suffix, "settle" + suffix + "@example.com", "password123");
    }

    private Address newAddress(Long userId) {
        return addressRepository.save(
                new Address(
                        userId,
                        "Bob",
                        "13800002222",
                        "Beijing",
                        "Beijing",
                        "Chaoyang",
                        "1 Settle Rd",
                        true));
    }

    private ProductVariant newSku(String spu, String sku, String price, int stock) {
        Product product =
                catalogService.createDraft(
                        new CreateProductCommand(
                                spu,
                                "Settle " + spu,
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
