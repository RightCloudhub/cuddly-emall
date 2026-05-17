package com.example.mall.integration.web;

import com.example.mall.application.order.OrderQueryService;
import com.example.mall.application.order.OrderQueryService.OrderView;
import com.example.mall.domain.order.OrderItem;
import com.example.mall.domain.shipment.Shipment;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * I1: Order lookup webhook. AskFlow's {@code search_order} tool calls this with the order_no it
 * extracted from the user message. Auth: service token (handled by {@link
 * IntegrationSecurityConfig}). 404 lets AskFlow fall back to its mock data.
 */
@RestController
@RequestMapping("/api/v1/integration/orders")
public class OrderLookupController {

    private static final Pattern ORDER_NO = Pattern.compile("^MO\\d{12}$");

    private final OrderQueryService orderQueryService;

    public OrderLookupController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/lookup")
    public ResponseEntity<OrderLookupResponse> lookup(@RequestParam("order_id") String orderId) {
        if (orderId == null || !ORDER_NO.matcher(orderId).matches()) {
            // Same shape as 404 — AskFlow doesn't need to distinguish.
            return ResponseEntity.notFound().build();
        }
        Optional<OrderView> view = orderQueryService.getByOrderNoAdmin(orderId);
        return view.map(v -> ResponseEntity.ok(toResponse(v)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private OrderLookupResponse toResponse(OrderView v) {
        Shipment shipment = v.shipment();
        List<OrderLookupResponse.Item> items =
                v.items().stream().map(OrderLookupController::toItem).toList();
        return new OrderLookupResponse(
                v.order().getOrderNo(),
                v.order().getStatus().name().toLowerCase(),
                shipment == null ? null : shipment.getTrackingNo(),
                shipment == null ? "2-3 business days" : shipment.getEstimatedDelivery(),
                items,
                v.order().getTotal().toPlainString(),
                v.order().getCurrency());
    }

    private static OrderLookupResponse.Item toItem(OrderItem it) {
        return new OrderLookupResponse.Item(it.getSkuCode(), it.getTitle(), it.getQty());
    }
}
