package com.example.mall.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.mall.application.user.UserRegistrationService;
import com.example.mall.domain.outbox.UserOutboxEntry;
import com.example.mall.domain.outbox.UserOutboxRepository;
import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserIdMappingRepository;
import com.example.mall.integration.askflow.AskFlowApiClient;
import com.example.mall.support.PostgresBackedTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "mall.askflow.kb-fixed-delay-ms=600000",
            "mall.askflow.user-fixed-delay-ms=600000",
            "mall.askflow.service-token=test-service-token"
        })
class UserSyncWorkerIntegrationTest extends PostgresBackedTest {

    @Autowired UserSyncWorker userSyncWorker;
    @Autowired UserRegistrationService userRegistrationService;
    @Autowired UserOutboxRepository userOutboxRepository;
    @Autowired UserIdMappingRepository mappingRepository;
    @MockBean AskFlowApiClient askFlowApiClient;

    @Test
    void registrationEnqueuesOutboxAndWorkerWritesMapping() {
        UUID expected = UUID.randomUUID();
        when(askFlowApiClient.createUser(any(), any(), any())).thenReturn(expected);

        User u = userRegistrationService.register(
                "syncuser", "syncuser@example.com", "password123");

        // outbox row exists, mapping not yet
        assertThat(userOutboxRepository.countByStatus(UserOutboxEntry.Status.pending))
                .isGreaterThanOrEqualTo(1);
        assertThat(mappingRepository.findById(u.getId())).isEmpty();

        userSyncWorker.drainOnce();

        assertThat(mappingRepository.findById(u.getId())).isPresent();
        assertThat(mappingRepository.findById(u.getId()).get().getAskflowUserId()).isEqualTo(expected);
        assertThat(userOutboxRepository.countByStatus(UserOutboxEntry.Status.pending)).isZero();
    }
}
