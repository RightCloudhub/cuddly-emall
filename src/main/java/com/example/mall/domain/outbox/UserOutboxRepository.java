package com.example.mall.domain.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserOutboxRepository extends JpaRepository<UserOutboxEntry, Long> {

    List<UserOutboxEntry> findByStatusOrderByIdAsc(UserOutboxEntry.Status status);

    long countByStatus(UserOutboxEntry.Status status);

    @Query(
            value =
                    "SELECT * FROM mall_user_outbox "
                            + "WHERE status = 'pending' "
                            + "ORDER BY id ASC "
                            + "LIMIT :limit "
                            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<UserOutboxEntry> lockNextBatch(int limit);
}
