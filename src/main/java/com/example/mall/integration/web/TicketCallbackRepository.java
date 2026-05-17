package com.example.mall.integration.web;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCallbackRepository extends JpaRepository<TicketCallback, Long> {

    boolean existsByTicketIdAndStatus(String ticketId, String status);
}
