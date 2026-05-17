package com.example.mall.application.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mall.domain.user.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            "test-secret-must-be-at-least-32-bytes-long-yes-it-is";

    @Test
    void roundtripIssueAndParse() {
        JwtService svc = new JwtService(SECRET, "mall", 3600);
        User user = new User("alice", "alice@example.com", "hash");
        setId(user, 42L);

        String token = svc.issueAccessToken(user);
        Claims claims = svc.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
        assertThat(claims.get("email", String.class)).isEqualTo("alice@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getIssuer()).isEqualTo("mall");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void parseRejectsTamperedToken() {
        JwtService svc = new JwtService(SECRET, "mall", 3600);
        User user = new User("alice", "alice@example.com", "hash");
        setId(user, 1L);
        String token = svc.issueAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        org.junit.jupiter.api.Assertions.assertThrows(
                io.jsonwebtoken.JwtException.class, () -> svc.parse(tampered));
    }

    @Test
    void rejectsShortSecret() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new JwtService("too-short", "mall", 3600));
    }

    private static void setId(User user, Long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
