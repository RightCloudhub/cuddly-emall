package com.example.mall.web.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaceOrderRequest(
        @NotNull Long shippingAddressId,
        @Size(max = 32) String couponCode,
        @NotEmpty @Valid List<Item> items) {

    public record Item(@NotNull Long skuId, @Min(1) int qty) {}
}
