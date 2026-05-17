package com.example.mall.integration.web;

import com.example.mall.domain.user.UserIdMappingRepository;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * I3: Ticket backflow. AskFlow notifies the mall when a ticket changes state. Idempotent on {@code
 * (ticket_id, status)} via the unique index on {@code mall_ticket_callbacks}.
 *
 * <p>Auth: service token (see {@link IntegrationSecurityConfig}). We record receipt and resolve
 * {@code askflow_user_id → mall_user_id} via {@code user_id_mapping}; downstream notification
 * fan-out is a TODO follow-up (deliberately out of scope for PR4).
 */
@RestController
@RequestMapping("/api/v1/integration/tickets")
public class TicketCallbackController {

    private final TicketCallbackRepository callbackRepository;
    private final UserIdMappingRepository mappingRepository;

    public TicketCallbackController(
            TicketCallbackRepository callbackRepository,
            UserIdMappingRepository mappingRepository) {
        this.callbackRepository = callbackRepository;
        this.mappingRepository = mappingRepository;
    }

    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Map<String, Object>> receive(
            @Valid @RequestBody TicketCallbackRequest req) {
        Long mallUserId =
                req.askflowUserId() == null
                        ? null
                        : mappingRepository
                                .findByAskflowUserId(req.askflowUserId())
                                .map(m -> m.getMallUserId())
                                .orElse(null);

        // Fast-path: short-circuit on known duplicate before any insert.
        if (callbackRepository.existsByTicketIdAndStatus(req.ticketId(), req.status())) {
            return ResponseEntity.ok(response("duplicate", mallUserId));
        }

        try {
            callbackRepository.save(
                    new TicketCallback(
                            req.ticketId(),
                            req.status(),
                            req.askflowUserId(),
                            mallUserId,
                            req.title()));
        } catch (DataIntegrityViolationException duplicate) {
            // Concurrent duplicate slipped through the existsBy check — still OK.
            return ResponseEntity.ok(response("duplicate", mallUserId));
        }
        return ResponseEntity.ok(response("accepted", mallUserId));
    }

    private static Map<String, Object> response(String status, Long mallUserId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("mall_user_id", mallUserId);
        return body;
    }
}
