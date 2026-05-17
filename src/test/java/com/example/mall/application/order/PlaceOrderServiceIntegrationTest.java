package com.example.mall.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.application.catalog.CatalogService;
import com.example.mall.application.inventory.InventoryService;
import com.example.mall.application.payment.MockPaymentGateway;
import com.example.mall.application.user.UserRegistrationService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.inventory.InsufficientStockException;
import com.example.mall.domain.order.OrderRepository;
import com.example.mall.domain.payment.PaymentIntent;
import com.example.mall.domain.payment.PaymentIntentRepository;
import com.example.mall.domain.payment.PaymentIntentStatus;
import com.example.mall.domain.promotion.CouponType;
import com.example.mall.domain.user.Address;
import com.example.mall.domain.user.AddressRepository;
import com.example.mall.domain.user.User;
import com.example.mall.support.PostgresBackedTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {"mall.payment.mock.settlement-delay-ms=600000"})
class PlaceOrderServiceIntegrationTest extends PostgresBackedTest {

    private static final Pattern ORDER_NO = Pattern.compile("^MO\\d{12}$");

    @Autowired PlaceOrderService placeOrderService;
    @Autowired CatalogService catalogService;
    @Autowired InventoryService inventoryService;
    @Autowired UserRegistrationService userRegistrationService;
    @Autowired AddressRepository addressRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentIntentRepository paymentIntentRepository;
    @Autowired com.example.mall.application.promotion.CouponService couponService;

    @Test
    void placeOrderHappyPath() {
        User user = newUser();
        Address address = newAddress(user.getId());
        ProductVariant variant = newPublishedSku("SPU-OK-1", "SKU-OK-1", "12.50", 20);

        PlaceOrderResult result =
                placeOrderService.place(
                        new PlaceOrderCommand(
                                user.getId(),
                                address.getId(),
                                null,
                                List.of(new PlaceOrderCommand.LineItem(variant.getId(), 2))));

        assertThat(ORDER_NO.matcher(result.order().getOrderNo()).matches()).isTrue();
        assertThat(result.order().getSubtotal()).isEqualByComparingTo("25.00");
        assertThat(result.order().getDiscount()).isEqualByComparingTo("0");
        assertThat(result.order().getTotal()).isEqualByComparingTo("25.00");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getSkuCode()).isEqualTo("SKU-OK-1");

        PaymentIntent intent = result.intent();
        assertThat(intent.getGateway()).isEqualTo(MockPaymentGateway.NAME);
        assertThat(intent.getGatewayIntentId()).startsWith("mock_pi_");
        assertThat(intent.getStatus()).isEqualTo(PaymentIntentStatus.REQUIRES_ACTION);

        // Inventory was reserved.
        var inv = inventoryService.get(variant.getId());
        assertThat(inv.getReserved()).isEqualTo(2);
        assertThat(inv.getAvailable()).isEqualTo(18);

        // Order is persisted and discoverable by order_no.
        assertThat(orderRepository.findByOrderNo(result.order().getOrderNo())).isPresent();
        assertThat(paymentIntentRepository.findByOrderId(result.order().getId())).hasSize(1);
    }

    @Test
    void placingOrderWithoutStockFailsAndRollsBack() {
        User user = newUser();
        Address address = newAddress(user.getId());
        ProductVariant variant = newPublishedSku("SPU-NO-STOCK", "SKU-NO-STOCK", "9.99", 1);

        // First order takes the only unit.
        placeOrderService.place(
                new PlaceOrderCommand(
                        user.getId(),
                        address.getId(),
                        null,
                        List.of(new PlaceOrderCommand.LineItem(variant.getId(), 1))));

        // Second attempt requests one more — inventory is empty.
        assertThatThrownBy(
                        () ->
                                placeOrderService.place(
                                        new PlaceOrderCommand(
                                                user.getId(),
                                                address.getId(),
                                                null,
                                                List.of(
                                                        new PlaceOrderCommand.LineItem(
                                                                variant.getId(), 1)))))
                .isInstanceOf(InsufficientStockException.class);

        // Only one order exists.
        assertThat(orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).hasSize(1);
        var inv = inventoryService.get(variant.getId());
        assertThat(inv.getReserved()).isEqualTo(1);
        assertThat(inv.getAvailable()).isZero();
    }

    @Test
    void couponDiscountAppliesToTotal() {
        User user = newUser();
        Address address = newAddress(user.getId());
        ProductVariant variant = newPublishedSku("SPU-CPN-1", "SKU-CPN-1", "50.00", 5);

        couponService.create(
                "WELCOME10",
                CouponType.PERCENT,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                null);

        PlaceOrderResult result =
                placeOrderService.place(
                        new PlaceOrderCommand(
                                user.getId(),
                                address.getId(),
                                "WELCOME10",
                                List.of(new PlaceOrderCommand.LineItem(variant.getId(), 2))));

        assertThat(result.order().getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(result.order().getDiscount()).isEqualByComparingTo("10.0000");
        assertThat(result.order().getTotal()).isEqualByComparingTo("90.0000");
        assertThat(result.order().getCouponId()).isNotNull();
    }

    @Test
    void anotherUsersAddressIsRejected() {
        User alice = newUser();
        User bob = newUser();
        Address aliceAddress = newAddress(alice.getId());
        ProductVariant variant = newPublishedSku("SPU-OWN-A", "SKU-OWN-A", "1.00", 10);

        assertThatThrownBy(
                        () ->
                                placeOrderService.place(
                                        new PlaceOrderCommand(
                                                bob.getId(),
                                                aliceAddress.getId(),
                                                null,
                                                List.of(
                                                        new PlaceOrderCommand.LineItem(
                                                                variant.getId(), 1)))))
                .isInstanceOf(com.example.mall.web.error.NotFoundException.class);
    }

    private User newUser() {
        String suffix = String.valueOf(System.nanoTime());
        return userRegistrationService.register(
                "buyer" + suffix, "buyer" + suffix + "@example.com", "password123");
    }

    private Address newAddress(Long userId) {
        return addressRepository.save(
                new Address(
                        userId,
                        "Alice",
                        "13800001111",
                        "Shanghai",
                        "Shanghai",
                        "Pudong",
                        "1234 Test Rd",
                        true));
    }

    private ProductVariant newPublishedSku(String spu, String sku, String price, int stock) {
        Product product =
                catalogService.createDraft(
                        new CreateProductCommand(
                                spu,
                                "Test " + spu,
                                "desc",
                                null,
                                "30 day returns",
                                List.of(
                                        new VariantInput(
                                                sku,
                                                Map.of(),
                                                new BigDecimal(price),
                                                100))));
        ProductVariant v = catalogService.variantsOf(product.getId()).get(0);
        inventoryService.restock(v.getId(), stock);
        return v;
    }
}
