package com.example.mall.web.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 64) String recipient,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 64) String province,
        @NotBlank @Size(max = 64) String city,
        @NotBlank @Size(max = 64) String district,
        @NotBlank @Size(max = 255) String detail,
        boolean isDefault) {}
