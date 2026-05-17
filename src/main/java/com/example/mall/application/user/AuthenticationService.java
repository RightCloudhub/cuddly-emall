package com.example.mall.application.user;

import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserRepository;
import com.example.mall.web.error.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResult login(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        User user =
                userRepository
                        .findByEmail(normalizedEmail)
                        .orElseThrow(() -> new UnauthorizedException("invalid credentials"));
        if (user.getStatus() != User.Status.ACTIVE) {
            throw new UnauthorizedException("account disabled");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("invalid credentials");
        }
        String token = jwtService.issueAccessToken(user);
        return new AuthResult(user, token, jwtService.getAccessTokenTtlSeconds());
    }

    public record AuthResult(User user, String accessToken, long expiresInSeconds) {}
}
