package com.example.mall.domain.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdMappingRepository extends JpaRepository<UserIdMapping, Long> {
    Optional<UserIdMapping> findByAskflowUserId(UUID askflowUserId);
}
