package com.example.mall.application.integration;

import com.example.mall.application.user.JwtService;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues JWTs that AskFlow will trust. Shares the HS256 secret with {@link JwtService} via the
 * {@code mall.jwt.secret} property — AskFlow's {@code SECRET_KEY} must equal this. Used only by
 * the auth bridge (I4).
 */
@Service
public class AskFlowJwtService {

    private final JwtService mallJwtService;
    private final long ttlSeconds;

    public AskFlowJwtService(
            JwtService mallJwtService,
            @Value("${mall.askflow.bridge-token-ttl-seconds:3600}") long ttlSeconds) {
        this.mallJwtService = mallJwtService;
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedToken issueForAskflowUser(UUID askflowUserId, String username, String email) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        String token =
                Jwts.builder()
                        .issuer("mall-bridge")
                        .subject(askflowUserId.toString())
                        .claim("username", username)
                        .claim("email", email)
                        .claim("role", "user")
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(exp))
                        .signWith(mallJwtService.getKey(), Jwts.SIG.HS256)
                        .compact();
        return new IssuedToken(token, exp);
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
