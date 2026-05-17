package com.example.mall.integration.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderLookupResponse(
        @JsonProperty("order_id") String orderId,
        String status,
        String tracking,
        @JsonProperty("estimated_delivery") String estimatedDelivery,
        List<Item> items,
        String total,
        String currency) {

    public record Item(String sku, String title, int qty) {}
}
