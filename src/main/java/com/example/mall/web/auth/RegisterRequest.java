package com.example.mall.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
                @Size(min = 3, max = 64)
                @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username must be alphanumeric/underscore")
                String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String password) {}
