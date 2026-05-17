package com.example.mall.application.order;

import com.example.mall.domain.order.Order;
import com.example.mall.domain.order.OrderItem;
import com.example.mall.domain.order.OrderItemRepository;
import com.example.mall.domain.order.OrderRepository;
import com.example.mall.domain.payment.PaymentIntent;
import com.example.mall.domain.payment.PaymentIntentRepository;
import com.example.mall.domain.shipment.Shipment;
import com.example.mall.domain.shipment.ShipmentRepository;
import com.example.mall.web.error.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-side queries for orders. Ownership-enforced — users can only see their own orders. */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final ShipmentRepository shipmentRepository;

    public OrderQueryService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentIntentRepository paymentIntentRepository,
            ShipmentRepository shipmentRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional(readOnly = true)
    public OrderView getForUser(Long orderId, Long userId) {
        Order order =
                orderRepository
                        .findByIdAndUserId(orderId, userId)
                        .orElseThrow(() -> new NotFoundException("order not found"));
        return assemble(order);
    }

    @Transactional(readOnly = true)
    public OrderView getByOrderNoForUser(String orderNo, Long userId) {
        Order order =
                orderRepository
                        .findByOrderNo(orderNo)
                        .filter(o -> o.getUserId().equals(userId))
                        .orElseThrow(() -> new NotFoundException("order not found"));
        return assemble(order);
    }

    @Transactional(readOnly = true)
    public Optional<OrderView> getByOrderNoAdmin(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).map(this::assemble);
    }

    @Transactional(readOnly = true)
    public OrderView getByIdAdmin(Long orderId) {
        Order order =
                orderRepository
                        .findById(orderId)
                        .orElseThrow(() -> new NotFoundException("order not found"));
        return assemble(order);
    }

    @Transactional(readOnly = true)
    public List<OrderView> listForUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::assemble)
                .toList();
    }

    private OrderView assemble(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        Optional<PaymentIntent> intent =
                paymentIntentRepository.findByOrderId(order.getId()).stream().findFirst();
        Optional<Shipment> shipment = shipmentRepository.findByOrderId(order.getId());
        return new OrderView(order, items, intent.orElse(null), shipment.orElse(null));
    }

    public record OrderView(
            Order order, List<OrderItem> items, PaymentIntent intent, Shipment shipment) {}
}
