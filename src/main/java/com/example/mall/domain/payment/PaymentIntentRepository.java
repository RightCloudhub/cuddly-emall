package com.example.mall.domain.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {

    Optional<PaymentIntent> findByGatewayIntentId(String gatewayIntentId);

    List<PaymentIntent> findByOrderId(Long orderId);
}
