package com.example.mall.application.payment;

import com.example.mall.application.inventory.InventoryService;
import com.example.mall.application.shipment.ShipmentService;
import com.example.mall.domain.order.Order;
import com.example.mall.domain.order.OrderItem;
import com.example.mall.domain.order.OrderItemRepository;
import com.example.mall.domain.order.OrderRepository;
import com.example.mall.domain.order.OrderStatus;
import com.example.mall.domain.payment.PaymentEvent;
import com.example.mall.domain.payment.PaymentEventRepository;
import com.example.mall.domain.payment.PaymentIntent;
import com.example.mall.domain.payment.PaymentIntentRepository;
import com.example.mall.domain.payment.PaymentIntentStatus;
import com.example.mall.web.error.NotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies verified gateway events to the domain. Used by both {@code PaymentWebhookController}
 * (real gateways) and {@code MockPaymentGateway}'s scheduled callback.
 *
 * <p>Idempotency is enforced by the unique constraint on {@code payment_events.gateway_event_id}:
 * re-delivery of the same event is a no-op after the first call.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public static final String EVENT_PAYMENT_SUCCEEDED = "payment_succeeded";
    public static final String EVENT_PAYMENT_FAILED = "payment_failed";

    private final PaymentIntentRepository intentRepository;
    private final PaymentEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final ShipmentService shipmentService;

    public PaymentService(
            PaymentIntentRepository intentRepository,
            PaymentEventRepository eventRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            InventoryService inventoryService,
            ShipmentService shipmentService) {
        this.intentRepository = intentRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryService = inventoryService;
        this.shipmentService = shipmentService;
    }

    @Transactional
    public ApplyResult applyEvent(String gatewayName, GatewayEvent event) {
        if (eventRepository.existsByGatewayEventId(event.gatewayEventId())) {
            log.info(
                    "ignoring duplicate gateway event {} (gateway={})",
                    event.gatewayEventId(),
                    gatewayName);
            return ApplyResult.duplicate(event.gatewayEventId());
        }

        PaymentIntent intent =
                intentRepository
                        .findByGatewayIntentId(event.gatewayIntentId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "intent not found for gateway id "
                                                        + event.gatewayIntentId()));
        if (!intent.getGateway().equalsIgnoreCase(gatewayName)) {
            throw new IllegalStateException(
                    "gateway mismatch: event came from "
                            + gatewayName
                            + " but intent uses "
                            + intent.getGateway());
        }

        eventRepository.save(
                new PaymentEvent(
                        intent.getId(), event.eventType(), event.gatewayEventId(), event.raw()));

        switch (event.eventType()) {
            case EVENT_PAYMENT_SUCCEEDED -> handleSucceeded(intent);
            case EVENT_PAYMENT_FAILED -> handleFailed(intent);
            default -> log.info("noop for event type {}", event.eventType());
        }
        return ApplyResult.applied(event.gatewayEventId(), event.eventType());
    }

    private void handleSucceeded(PaymentIntent intent) {
        if (intent.getStatus() == PaymentIntentStatus.SUCCEEDED) {
            return;
        }
        intent.markStatus(PaymentIntentStatus.SUCCEEDED);

        Order order =
                orderRepository
                        .findById(intent.getOrderId())
                        .orElseThrow(() -> new NotFoundException("order not found for intent"));
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.markPaid();
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem it : items) {
                inventoryService.deductReserved(it.getSkuId(), it.getQty());
            }
            // Shipment is created in PENDING — a warehouse worker / admin advances it to SHIPPED.
            shipmentService.createForOrder(order.getId());
        }
    }

    private void handleFailed(PaymentIntent intent) {
        if (intent.getStatus() == PaymentIntentStatus.FAILED) {
            return;
        }
        intent.markStatus(PaymentIntentStatus.FAILED);

        Order order =
                orderRepository
                        .findById(intent.getOrderId())
                        .orElseThrow(() -> new NotFoundException("order not found for intent"));
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            // Release inventory reservation; cancel order.
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem it : items) {
                inventoryService.release(it.getSkuId(), it.getQty());
            }
            order.cancel();
        }
    }

    public record ApplyResult(String gatewayEventId, String eventType, boolean duplicate) {
        public static ApplyResult duplicate(String id) {
            return new ApplyResult(id, null, true);
        }

        public static ApplyResult applied(String id, String type) {
            return new ApplyResult(id, type, false);
        }
    }
}
