package com.example.mall.application.user;

import com.example.mall.domain.outbox.UserOutboxEntry;
import com.example.mall.domain.outbox.UserOutboxRepository;
import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserRepository;
import com.example.mall.web.error.ConflictException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserOutboxRepository outboxRepository;

    public UserRegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserOutboxRepository outboxRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public User register(String username, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("email already in use");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("username already in use");
        }
        User user = new User(username, normalizedEmail, passwordEncoder.encode(rawPassword));
        user = userRepository.save(user);

        // Same-transaction outbox row → UserSyncWorker drains it into AskFlow and writes the
        // user_id_mapping row. Until that completes, the mall user has no askflow identity yet —
        // the auth bridge will 404 in that window, which is the expected behavior.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("external_id", String.valueOf(user.getId()));
        outboxRepository.save(new UserOutboxEntry(UserOutboxEntry.Op.create, user.getId(), payload));

        return user;
    }
}
