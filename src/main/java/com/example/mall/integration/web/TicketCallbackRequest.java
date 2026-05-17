package com.example.mall.integration.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record TicketCallbackRequest(
        @JsonProperty("ticket_id") @NotBlank String ticketId,
        @NotBlank String status,
        @JsonProperty("askflow_user_id") UUID askflowUserId,
        String type,
        String title) {}
