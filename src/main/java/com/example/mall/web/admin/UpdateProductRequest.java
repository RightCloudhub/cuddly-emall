package com.example.mall.web.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProductRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 10000) String description,
        Long categoryId,
        @Size(max = 10000) String policySnippet,
        @NotEmpty @Valid List<VariantPayload> variants) {}
