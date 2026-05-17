package com.example.mall.integration.outbox;

import com.example.mall.domain.outbox.UserOutboxEntry;
import com.example.mall.domain.outbox.UserOutboxRepository;
import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserIdMapping;
import com.example.mall.domain.user.UserIdMappingRepository;
import com.example.mall.domain.user.UserRepository;
import com.example.mall.integration.AskFlowProperties;
import com.example.mall.integration.askflow.AskFlowApiClient;
import com.example.mall.integration.askflow.AskFlowApiException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains {@code mall_user_outbox} by calling AskFlow's {@code POST /api/v1/admin/users}. On
 * success writes the {@code (mall_user_id, askflow_user_id)} into {@code user_id_mapping} so the
 * auth bridge can swap tokens.
 */
@Component
public class UserSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(UserSyncWorker.class);

    private final UserOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final UserIdMappingRepository mappingRepository;
    private final AskFlowApiClient askFlow;
    private final AskFlowProperties props;

    public UserSyncWorker(
            UserOutboxRepository outboxRepository,
            UserRepository userRepository,
            UserIdMappingRepository mappingRepository,
            AskFlowApiClient askFlow,
            AskFlowProperties props) {
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.mappingRepository = mappingRepository;
        this.askFlow = askFlow;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${mall.askflow.user-fixed-delay-ms:5000}")
    public void tick() {
        try {
            int processed = drainOnce();
            if (processed > 0) {
                log.debug("user-sync drained {} rows", processed);
            }
        } catch (RuntimeException e) {
            log.warn("user-sync tick failed: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int drainOnce() {
        List<UserOutboxEntry> batch = outboxRepository.lockNextBatch(props.getBatchSize());
        for (UserOutboxEntry row : batch) {
            try {
                process(row);
                row.markProcessed();
            } catch (RuntimeException e) {
                row.recordFailure(truncate(e.getMessage()), props.getUserMaxRetries());
                if (row.getStatus() == UserOutboxEntry.Status.dead) {
                    log.error(
                            "user-sync row {} (user_id={}) dead after {} retries: {}",
                            row.getId(),
                            row.getUserId(),
                            row.getRetryCount(),
                            e.getMessage());
                } else {
                    log.warn(
                            "user-sync row {} failed (retry {}): {}",
                            row.getId(),
                            row.getRetryCount(),
                            e.getMessage());
                }
            }
        }
        return batch.size();
    }

    private void process(UserOutboxEntry row) {
        User user =
                userRepository
                        .findById(row.getUserId())
                        .orElseThrow(
                                () ->
                                        new AskFlowApiException(
                                                "user disappeared before sync: " + row.getUserId(),
                                                -1));
        if (mappingRepository.findById(user.getId()).isPresent() && row.getOp() == UserOutboxEntry.Op.create) {
            // Already synced — nothing to do (idempotent re-emit).
            return;
        }
        UUID askflowUserId =
                askFlow.createUser(user.getUsername(), user.getEmail(), String.valueOf(user.getId()));
        if (mappingRepository.findById(user.getId()).isEmpty()) {
            mappingRepository.save(new UserIdMapping(user.getId(), askflowUserId));
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
