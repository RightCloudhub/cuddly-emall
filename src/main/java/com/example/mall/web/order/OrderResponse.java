package com.example.mall.web.order;

import com.example.mall.application.order.OrderQueryService.OrderView;
import com.example.mall.domain.order.Order;
import com.example.mall.domain.order.OrderItem;
import com.example.mall.domain.payment.PaymentIntent;
import com.example.mall.domain.shipment.Shipment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OrderResponse(
        Long id,
        String orderNo,
        String status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String currency,
        Long couponId,
        Map<String, Object> shippingAddress,
        Instant createdAt,
        Instant paidAt,
        Instant shippedAt,
        Instant completedAt,
        Instant cancelledAt,
        List<Item> items,
        PaymentIntentSummary payment,
        ShipmentSummary shipment) {

    public static OrderResponse from(OrderView view) {
        Order o = view.order();
        return new OrderResponse(
                o.getId(),
                o.getOrderNo(),
                o.getStatus().name(),
                o.getSubtotal(),
                o.getDiscount(),
                o.getTotal(),
                o.getCurrency(),
                o.getCouponId(),
                o.getShippingAddressSnapshot(),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShippedAt(),
                o.getCompletedAt(),
                o.getCancelledAt(),
                view.items().stream().map(Item::from).toList(),
                view.intent() == null ? null : PaymentIntentSummary.from(view.intent()),
                view.shipment() == null ? null : ShipmentSummary.from(view.shipment()));
    }

    public record Item(
            Long id,
            Long skuId,
            String skuCode,
            String title,
            BigDecimal unitPrice,
            int qty,
            BigDecimal lineTotal) {
        public static Item from(OrderItem it) {
            return new Item(
                    it.getId(),
                    it.getSkuId(),
                    it.getSkuCode(),
                    it.getTitle(),
                    it.getUnitPrice(),
                    it.getQty(),
                    it.getLineTotal());
        }
    }

    public record PaymentIntentSummary(
            Long id, String gateway, String status, String gatewayIntentId, BigDecimal amount) {
        public static PaymentIntentSummary from(PaymentIntent intent) {
            return new PaymentIntentSummary(
                    intent.getId(),
                    intent.getGateway(),
                    intent.getStatus().name(),
                    intent.getGatewayIntentId(),
                    intent.getAmount());
        }
    }

    public record ShipmentSummary(
            Long id,
            String trackingNo,
            String carrier,
            String status,
            String estimatedDelivery,
            Instant shippedAt,
            Instant deliveredAt) {
        public static ShipmentSummary from(Shipment s) {
            return new ShipmentSummary(
                    s.getId(),
                    s.getTrackingNo(),
                    s.getCarrier(),
                    s.getStatus().name(),
                    s.getEstimatedDelivery(),
                    s.getShippedAt(),
                    s.getDeliveredAt());
        }
    }
}
