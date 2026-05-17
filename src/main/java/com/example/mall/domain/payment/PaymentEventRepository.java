package com.example.mall.domain.payment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    boolean existsByGatewayEventId(String gatewayEventId);

    List<PaymentEvent> findByIntentIdOrderByReceivedAtAsc(Long intentId);
}
