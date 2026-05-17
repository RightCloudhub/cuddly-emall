package com.example.mall.integration.web;

import com.example.mall.application.integration.AskFlowJwtService;
import com.example.mall.application.integration.AskFlowJwtService.IssuedToken;
import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserIdMapping;
import com.example.mall.domain.user.UserIdMappingRepository;
import com.example.mall.domain.user.UserRepository;
import com.example.mall.web.error.NotFoundException;
import com.example.mall.web.error.UnauthorizedException;
import com.example.mall.web.security.JwtAuthenticationFilter.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * I4: Auth bridge. Trades a mall JWT for an AskFlow JWT signed with the same secret. The chat
 * widget calls this once at mount, then opens a WebSocket to AskFlow with the returned token.
 *
 * <p>Note: this endpoint is intentionally <em>not</em> covered by the service-token filter — the
 * caller IS a real mall user, not an AskFlow-side service. The main JWT filter chain handles it.
 */
@RestController
@RequestMapping("/api/v1/integration/auth")
public class AuthBridgeController {

    private final UserRepository userRepository;
    private final UserIdMappingRepository mappingRepository;
    private final AskFlowJwtService askFlowJwtService;

    public AuthBridgeController(
            UserRepository userRepository,
            UserIdMappingRepository mappingRepository,
            AskFlowJwtService askFlowJwtService) {
        this.userRepository = userRepository;
        this.mappingRepository = mappingRepository;
        this.askFlowJwtService = askFlowJwtService;
    }

    @PostMapping("/bridge")
    public ResponseEntity<AuthBridgeResponse> bridge(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null || principal.id() == null) {
            // Defensive — the security chain should already have rejected this.
            throw new UnauthorizedException("authentication required");
        }
        Long mallUserId = principal.id();
        User user =
                userRepository
                        .findById(mallUserId)
                        .orElseThrow(() -> new NotFoundException("user not found"));
        UserIdMapping mapping =
                mappingRepository
                        .findById(mallUserId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "user not yet synced to askflow (mapping missing)"));

        IssuedToken issued =
                askFlowJwtService.issueForAskflowUser(
                        mapping.getAskflowUserId(), user.getUsername(), user.getEmail());

        return ResponseEntity.ok(
                new AuthBridgeResponse(
                        issued.token(), mapping.getAskflowUserId().toString(), issued.expiresAt().toString()));
    }
}
