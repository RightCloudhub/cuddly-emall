package com.example.mall.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.mall.domain.outbox.UserOutboxEntry;
import com.example.mall.domain.outbox.UserOutboxRepository;
import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserRepository;
import com.example.mall.web.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserOutboxRepository outboxRepository;
    @InjectMocks UserRegistrationService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registersNewUserWithLowercasedEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(false);

        User u = service.register("alice", "Alice@Example.COM", "password123");

        assertThat(u.getEmail()).isEqualTo("alice@example.com");
        assertThat(u.getUsername()).isEqualTo("alice");
        assertThat(u.getPasswordHash()).isEqualTo("bcrypt-hash");

        ArgumentCaptor<UserOutboxEntry> outbox = ArgumentCaptor.forClass(UserOutboxEntry.class);
        Mockito.verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getOp()).isEqualTo(UserOutboxEntry.Op.create);
        assertThat(outbox.getValue().getPayload()).containsEntry("email", "alice@example.com");
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register("alice", "alice@example.com", "password123"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email");
    }

    @Test
    void rejectsDuplicateUsername() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> service.register("alice", "alice@example.com", "password123"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("username");
    }
}
