package com.example.mall.domain.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface KbOutboxRepository extends JpaRepository<KbOutboxEntry, Long> {

    List<KbOutboxEntry> findByStatusOrderByIdAsc(KbOutboxEntry.Status status);

    long countByStatus(KbOutboxEntry.Status status);

    /**
     * Pending rows reserved for the current worker via {@code FOR UPDATE SKIP LOCKED}. Used by
     * {@code KbSyncWorker} in PR4; defined here so PR2 schema usage is exercised in tests.
     */
    @Query(
            value =
                    "SELECT * FROM mall_kb_outbox "
                            + "WHERE status = 'pending' "
                            + "ORDER BY id ASC "
                            + "LIMIT :limit "
                            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<KbOutboxEntry> lockNextBatch(int limit);
}
