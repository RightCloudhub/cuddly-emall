package com.example.mall.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserRepository;
import com.example.mall.web.error.UnauthorizedException;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String SECRET =
            "test-secret-must-be-at-least-32-bytes-long-yes-it-is";

    @Mock UserRepository userRepository;
    PasswordEncoder encoder;
    JwtService jwtService;
    AuthenticationService service;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(SECRET, "mall", 3600);
        service = new AuthenticationService(userRepository, encoder, jwtService);
    }

    @Test
    void loginIssuesTokenOnValidCredentials() {
        User u = new User("alice", "alice@example.com", encoder.encode("password123"));
        setId(u, 7L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(u));

        var result = service.login("Alice@Example.com", "password123");

        assertThat(result.user().getId()).isEqualTo(7L);
        assertThat(result.expiresInSeconds()).isEqualTo(3600);
        assertThat(jwtService.parse(result.accessToken()).getSubject()).isEqualTo("7");
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("nobody@example.com", "x"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        User u = new User("alice", "alice@example.com", encoder.encode("password123"));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login("alice@example.com", "wrong-password"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsDisabledAccount() {
        User u = new User("alice", "alice@example.com", encoder.encode("password123"));
        u.setStatus(User.Status.DISABLED);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login("alice@example.com", "password123"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void jwtServiceParseRejectsBadToken() {
        assertThatThrownBy(() -> jwtService.parse("not-a-jwt"))
                .isInstanceOf(JwtException.class);
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
