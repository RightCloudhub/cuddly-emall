package com.example.mall.application.order;

import com.example.mall.domain.order.Order;
import com.example.mall.domain.order.OrderItem;
import com.example.mall.domain.payment.PaymentIntent;
import java.util.List;

/** Result of {@link PlaceOrderService#place}. The HTTP layer maps this to a response DTO. */
public record PlaceOrderResult(Order order, List<OrderItem> items, PaymentIntent intent) {}
