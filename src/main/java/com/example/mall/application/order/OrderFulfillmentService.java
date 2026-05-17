package com.example.mall.application.order;

import com.example.mall.application.inventory.InventoryService;
import com.example.mall.domain.order.Order;
import com.example.mall.domain.order.OrderItemRepository;
import com.example.mall.domain.order.OrderRepository;
import com.example.mall.domain.order.OrderStatus;
import com.example.mall.domain.shipment.Shipment;
import com.example.mall.domain.shipment.ShipmentRepository;
import com.example.mall.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-side order state transitions. Customer-side actions live on {@link PlaceOrderService}. */
@Service
public class OrderFulfillmentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final InventoryService inventoryService;

    public OrderFulfillmentService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ShipmentRepository shipmentRepository,
            InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.shipmentRepository = shipmentRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Order markShipped(Long orderId) {
        Order order = require(orderId);
        Shipment shipment =
                shipmentRepository
                        .findByOrderId(orderId)
                        .orElseThrow(() -> new NotFoundException("shipment not found"));
        shipment.markShipped();
        order.markShipped();
        return order;
    }

    @Transactional
    public Order markDelivered(Long orderId) {
        Order order = require(orderId);
        Shipment shipment =
                shipmentRepository
                        .findByOrderId(orderId)
                        .orElseThrow(() -> new NotFoundException("shipment not found"));
        shipment.markDelivered();
        order.markCompleted();
        return order;
    }

    @Transactional
    public Order cancel(Long orderId) {
        Order order = require(orderId);
        // Release reservation only if the order has not yet been settled — after PAID the
        // inventory has been deducted and any reversal belongs to a refund flow.
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            orderItemRepository
                    .findByOrderId(orderId)
                    .forEach(it -> inventoryService.release(it.getSkuId(), it.getQty()));
        }
        order.cancel();
        return order;
    }

    private Order require(Long orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new NotFoundException("order not found"));
    }
}
