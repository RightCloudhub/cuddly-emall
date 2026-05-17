package com.example.mall.application.order;

import com.example.mall.application.inventory.InventoryService;
import com.example.mall.application.payment.GatewayIntentResult;
import com.example.mall.application.payment.PaymentGateway;
import com.example.mall.application.payment.PaymentGatewayRegistry;
import com.example.mall.application.promotion.CouponApplicationService;
import com.example.mall.application.promotion.CouponApplicationService.CouponQuote;
import com.example.mall.domain.catalog.ProductRepository;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.catalog.ProductVariantRepository;
import com.example.mall.domain.order.Order;
import com.example.mall.domain.order.OrderItem;
import com.example.mall.domain.order.OrderItemRepository;
import com.example.mall.domain.order.OrderRepository;
import com.example.mall.domain.payment.PaymentIntent;
import com.example.mall.domain.payment.PaymentIntentRepository;
import com.example.mall.domain.promotion.UserCouponRepository;
import com.example.mall.domain.user.Address;
import com.example.mall.domain.user.AddressRepository;
import com.example.mall.web.error.NotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places an order: validates the item list, computes totals (applying a coupon if provided),
 * reserves inventory, persists the order + items, and creates a payment intent via the configured
 * gateway. The whole thing runs in one transaction — if the gateway call fails, the inventory
 * reservation is rolled back.
 */
@Service
public class PlaceOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final AddressRepository addressRepository;
    private final CouponApplicationService couponApplicationService;
    private final UserCouponRepository userCouponRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final OrderNumberService orderNumberService;
    private final String defaultGateway;

    public PlaceOrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductVariantRepository variantRepository,
            ProductRepository productRepository,
            InventoryService inventoryService,
            AddressRepository addressRepository,
            CouponApplicationService couponApplicationService,
            UserCouponRepository userCouponRepository,
            PaymentIntentRepository paymentIntentRepository,
            PaymentGatewayRegistry gatewayRegistry,
            OrderNumberService orderNumberService,
            @Value("${mall.payment.default-gateway:MOCK}") String defaultGateway) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.addressRepository = addressRepository;
        this.couponApplicationService = couponApplicationService;
        this.userCouponRepository = userCouponRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.orderNumberService = orderNumberService;
        this.defaultGateway = defaultGateway;
    }

    @Transactional
    public PlaceOrderResult place(PlaceOrderCommand cmd) {
        if (cmd.items() == null || cmd.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }

        Address shippingAddress = resolveAddress(cmd.userId(), cmd.shippingAddressId());

        Map<Long, ProductVariant> variantsBySku = loadVariants(cmd.items());
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItemDraft> drafts = new ArrayList<>();
        for (PlaceOrderCommand.LineItem li : cmd.items()) {
            if (li.qty() <= 0) {
                throw new IllegalArgumentException("qty must be positive");
            }
            ProductVariant variant = variantsBySku.get(li.skuId());
            if (variant == null) {
                throw new NotFoundException("sku not found: " + li.skuId());
            }
            String title =
                    productRepository
                            .findById(variant.getProductId())
                            .map(p -> p.getTitle())
                            .orElse("");
            BigDecimal lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(li.qty()));
            subtotal = subtotal.add(lineTotal);
            drafts.add(
                    new OrderItemDraft(
                            variant.getId(),
                            variant.getSkuCode(),
                            title,
                            variant.getPrice(),
                            li.qty(),
                            lineTotal));
        }

        CouponQuote quote = couponApplicationService.quote(cmd.couponCode(), subtotal);

        // Reserve inventory (row-locked). Throws InsufficientStockException → rolls back the TX.
        for (PlaceOrderCommand.LineItem li : cmd.items()) {
            inventoryService.reserve(li.skuId(), li.qty());
        }

        Order order =
                orderRepository.save(
                        new Order(
                                orderNumberService.next(),
                                cmd.userId(),
                                subtotal,
                                quote.discount(),
                                quote.total(),
                                quote.couponId(),
                                snapshotAddress(shippingAddress)));
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemDraft d : drafts) {
            items.add(
                    orderItemRepository.save(
                            new OrderItem(
                                    order.getId(),
                                    d.skuId(),
                                    d.skuCode(),
                                    d.title(),
                                    d.unitPrice(),
                                    d.qty(),
                                    d.lineTotal())));
        }

        if (quote.couponId() != null) {
            userCouponRepository
                    .findByUserIdAndCouponId(cmd.userId(), quote.couponId())
                    .ifPresent(uc -> uc.markUsed(order.getId()));
        }

        PaymentGateway gateway = gatewayRegistry.require(defaultGateway);
        PaymentIntent intent =
                paymentIntentRepository.save(
                        new PaymentIntent(
                                order.getId(),
                                gateway.name(),
                                order.getTotal(),
                                order.getCurrency()));
        GatewayIntentResult gw =
                gateway.createIntent(order, order.getTotal(), order.getCurrency());
        intent.attachGatewayId(gw.gatewayIntentId());

        return new PlaceOrderResult(order, items, intent);
    }

    private Address resolveAddress(Long userId, Long addressId) {
        if (addressId == null) {
            throw new IllegalArgumentException("shippingAddressId is required");
        }
        Address address =
                addressRepository
                        .findById(addressId)
                        .orElseThrow(
                                () -> new NotFoundException("address not found: " + addressId));
        if (!address.getUserId().equals(userId)) {
            throw new NotFoundException("address not found: " + addressId);
        }
        return address;
    }

    private Map<Long, ProductVariant> loadVariants(List<PlaceOrderCommand.LineItem> items) {
        List<Long> skuIds = items.stream().map(PlaceOrderCommand.LineItem::skuId).toList();
        Map<Long, ProductVariant> out = new LinkedHashMap<>();
        for (ProductVariant v : variantRepository.findAllById(skuIds)) {
            out.put(v.getId(), v);
        }
        return out;
    }

    private static Map<String, Object> snapshotAddress(Address a) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("address_id", a.getId());
        snap.put("recipient", a.getRecipient());
        snap.put("phone", a.getPhone());
        snap.put("province", a.getProvince());
        snap.put("city", a.getCity());
        snap.put("district", a.getDistrict());
        snap.put("detail", a.getDetail());
        return snap;
    }

    private record OrderItemDraft(
            Long skuId,
            String skuCode,
            String title,
            BigDecimal unitPrice,
            int qty,
            BigDecimal lineTotal) {}
}
