package com.example.mall.web.auth;

import com.example.mall.application.user.AuthenticationService;
import com.example.mall.application.user.AuthenticationService.AuthResult;
import com.example.mall.application.user.JwtService;
import com.example.mall.application.user.UserRegistrationService;
import com.example.mall.domain.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final JwtService jwtService;

    public AuthController(
            UserRegistrationService registrationService,
            AuthenticationService authenticationService,
            JwtService jwtService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = registrationService.register(req.username(), req.email(), req.password());
        String token = jwtService.issueAccessToken(user);
        return ResponseEntity.ok(
                new AuthResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        token,
                        jwtService.getAccessTokenTtlSeconds()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthResult result = authenticationService.login(req.email(), req.password());
        User user = result.user();
        return ResponseEntity.ok(
                new AuthResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        result.accessToken(),
                        result.expiresInSeconds()));
    }
}
