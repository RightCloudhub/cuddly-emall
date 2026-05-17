package com.example.mall.application.user;

import com.example.mall.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * HS256 JWT issue/verify. Shares MALL_JWT_SECRET with AskFlow SECRET_KEY so tokens are mutually
 * verifiable (auth bridge in PR4 relies on this).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public JwtService(
            @Value("${mall.jwt.secret}") String secret,
            @Value("${mall.jwt.issuer:mall}") String issuer,
            @Value("${mall.jwt.access-token-ttl-seconds:86400}") long ttlSeconds) {
        this.key = resolveKey(secret);
        this.issuer = issuer;
        this.accessTokenTtlSeconds = ttlSeconds;
    }

    private static SecretKey resolveKey(String secret) {
        // Allow base64 secrets first; fall back to raw bytes. AskFlow's SECRET_KEY is typically raw.
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return Keys.hmacShaKeyFor(decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // not base64, fall through
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "mall.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        return Keys.hmacShaKeyFor(raw);
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException e) {
            throw new JwtException("invalid token: " + e.getMessage(), e);
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public SecretKey getKey() {
        return key;
    }
}
