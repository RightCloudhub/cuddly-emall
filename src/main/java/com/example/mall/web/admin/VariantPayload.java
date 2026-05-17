package com.example.mall.web.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record VariantPayload(
        @NotBlank @Size(max = 64) String skuCode,
        Map<String, String> attributes,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
        @Min(0) int weightG) {
    public VariantPayload {
        if (attributes == null) attributes = new LinkedHashMap<>();
    }
}
