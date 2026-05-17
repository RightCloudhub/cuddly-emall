package com.example.mall.web.auth;

public record AuthResponse(
        Long userId, String username, String email, String accessToken, long expiresInSeconds) {}
