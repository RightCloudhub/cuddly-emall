package com.example.mall.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mall.application.user.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AskFlowJwtServiceTest {

    private static final String SECRET = "shared-secret-that-is-at-least-thirty-two-bytes!!";

    private JwtService mallJwt;
    private AskFlowJwtService bridge;

    @BeforeEach
    void setUp() {
        mallJwt = new JwtService(SECRET, "mall", 3600);
        bridge = new AskFlowJwtService(mallJwt, 600);
    }

    @Test
    void signsTokenAskflowCanVerify() {
        UUID askflowUserId = UUID.randomUUID();
        var issued = bridge.issueForAskflowUser(askflowUserId, "alice", "alice@example.com");

        // Decode with the *same* HS256 secret AskFlow would use.
        Claims claims =
                Jwts.parser()
                        .verifyWith(mallJwt.getKey())
                        .build()
                        .parseSignedClaims(issued.token())
                        .getPayload();

        assertThat(claims.getSubject()).isEqualTo(askflowUserId.toString());
        assertThat(claims.get("username")).isEqualTo("alice");
        assertThat(claims.get("email")).isEqualTo("alice@example.com");
        assertThat(claims.get("role")).isEqualTo("user");
        assertThat(claims.getIssuer()).isEqualTo("mall-bridge");
        assertThat(issued.expiresAt()).isAfter(java.time.Instant.now());
    }
}
