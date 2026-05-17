package com.example.mall.integration.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoyaltyResponse(
        @JsonProperty("user_id") String userId,
        int points,
        String tier,
        @JsonProperty("expiring_soon") int expiringSoon) {}
