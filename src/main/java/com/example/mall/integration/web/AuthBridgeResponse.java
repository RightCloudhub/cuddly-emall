package com.example.mall.integration.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthBridgeResponse(
        @JsonProperty("askflow_token") String askflowToken,
        @JsonProperty("askflow_user_id") String askflowUserId,
        @JsonProperty("expires_at") String expiresAt) {}
