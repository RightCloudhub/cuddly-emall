package com.example.mall.application.order;

import java.util.List;

/**
 * Input record for {@link PlaceOrderService}. Items are pinned at the moment of order placement —
 * downstream price changes must not affect open orders.
 */
public record PlaceOrderCommand(
        Long userId, Long shippingAddressId, String couponCode, List<LineItem> items) {

    public record LineItem(Long skuId, int qty) {}
}
