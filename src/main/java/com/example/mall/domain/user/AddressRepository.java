package com.example.mall.domain.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByIsDefaultDescIdAsc(Long userId);

    Optional<Address> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update Address a set a.isDefault = false where a.userId = :userId and a.isDefault = true")
    int clearDefaultForUser(@Param("userId") Long userId);
}
