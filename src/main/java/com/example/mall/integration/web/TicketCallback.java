package com.example.mall.integration.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mall_ticket_callbacks")
public class TicketCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, length = 64)
    private String ticketId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "askflow_user_id")
    private UUID askflowUserId;

    @Column(name = "mall_user_id")
    private Long mallUserId;

    @Column(length = 255)
    private String title;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected TicketCallback() {}

    public TicketCallback(
            String ticketId, String status, UUID askflowUserId, Long mallUserId, String title) {
        this.ticketId = ticketId;
        this.status = status;
        this.askflowUserId = askflowUserId;
        this.mallUserId = mallUserId;
        this.title = title;
        this.receivedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getStatus() {
        return status;
    }

    public UUID getAskflowUserId() {
        return askflowUserId;
    }

    public Long getMallUserId() {
        return mallUserId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
