package com.example.mall.domain.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** PG SELECT ... FOR UPDATE — blocks concurrent reservations on the same SKU. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.skuId = :skuId")
    Optional<Inventory> findByIdForUpdate(@Param("skuId") Long skuId);
}
