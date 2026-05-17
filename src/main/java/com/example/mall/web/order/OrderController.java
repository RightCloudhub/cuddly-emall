package com.example.mall.web.order;

import com.example.mall.application.order.OrderQueryService;
import com.example.mall.application.order.PlaceOrderCommand;
import com.example.mall.application.order.PlaceOrderResult;
import com.example.mall.application.order.PlaceOrderService;
import com.example.mall.domain.order.Order;
import com.example.mall.domain.payment.PaymentIntent;
import com.example.mall.web.security.CurrentUser;
import com.example.mall.web.security.JwtAuthenticationFilter.AuthenticatedUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderService placeOrderService;
    private final OrderQueryService orderQueryService;

    public OrderController(
            PlaceOrderService placeOrderService, OrderQueryService orderQueryService) {
        this.placeOrderService = placeOrderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceOrderResponse place(
            @CurrentUser AuthenticatedUser principal,
            @Valid @RequestBody PlaceOrderRequest request) {
        List<PlaceOrderCommand.LineItem> items =
                request.items().stream()
                        .map(i -> new PlaceOrderCommand.LineItem(i.skuId(), i.qty()))
                        .toList();
        PlaceOrderCommand cmd =
                new PlaceOrderCommand(
                        principal.id(), request.shippingAddressId(), request.couponCode(), items);
        PlaceOrderResult result = placeOrderService.place(cmd);
        return PlaceOrderResponse.from(result);
    }

    @GetMapping
    public List<OrderResponse> list(@CurrentUser AuthenticatedUser principal) {
        return orderQueryService.listForUser(principal.id()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@CurrentUser AuthenticatedUser principal, @PathVariable Long id) {
        return OrderResponse.from(orderQueryService.getForUser(id, principal.id()));
    }

    @GetMapping("/by-number/{orderNo}")
    public OrderResponse getByNumber(
            @CurrentUser AuthenticatedUser principal, @PathVariable String orderNo) {
        return OrderResponse.from(orderQueryService.getByOrderNoForUser(orderNo, principal.id()));
    }

    public record PlaceOrderResponse(
            Long orderId,
            String orderNo,
            String status,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            String currency,
            PaymentIntentSummary payment) {
        public static PlaceOrderResponse from(PlaceOrderResult result) {
            Order order = result.order();
            PaymentIntent intent = result.intent();
            return new PlaceOrderResponse(
                    order.getId(),
                    order.getOrderNo(),
                    order.getStatus().name(),
                    order.getSubtotal(),
                    order.getDiscount(),
                    order.getTotal(),
                    order.getCurrency(),
                    new PaymentIntentSummary(
                            intent.getId(),
                            intent.getGateway(),
                            intent.getStatus().name(),
                            intent.getGatewayIntentId(),
                            intent.getAmount()));
        }
    }

    public record PaymentIntentSummary(
            Long id, String gateway, String status, String gatewayIntentId, BigDecimal amount) {}
}
