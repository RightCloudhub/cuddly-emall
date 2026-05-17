package com.example.mall.web.admin;

import com.example.mall.application.order.OrderFulfillmentService;
import com.example.mall.application.order.OrderQueryService;
import com.example.mall.web.error.NotFoundException;
import com.example.mall.web.order.OrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-side order operations. Routes are protected by {@code ROLE_ADMIN}. */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final OrderQueryService queryService;
    private final OrderFulfillmentService fulfillmentService;

    public AdminOrderController(
            OrderQueryService queryService, OrderFulfillmentService fulfillmentService) {
        this.queryService = queryService;
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return OrderResponse.from(queryService.getByIdAdmin(id));
    }

    @GetMapping("/by-number/{orderNo}")
    public OrderResponse getByNumber(@PathVariable String orderNo) {
        return OrderResponse.from(
                queryService
                        .getByOrderNoAdmin(orderNo)
                        .orElseThrow(() -> new NotFoundException("order not found")));
    }

    @PostMapping("/{id}/ship")
    public OrderResponse ship(@PathVariable Long id) {
        fulfillmentService.markShipped(id);
        return OrderResponse.from(queryService.getByIdAdmin(id));
    }

    @PostMapping("/{id}/deliver")
    public OrderResponse deliver(@PathVariable Long id) {
        fulfillmentService.markDelivered(id);
        return OrderResponse.from(queryService.getByIdAdmin(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        fulfillmentService.cancel(id);
        return OrderResponse.from(queryService.getByIdAdmin(id));
    }
}
