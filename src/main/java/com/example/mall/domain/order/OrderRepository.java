package com.example.mall.domain.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByOrderNo(String orderNo);
}
