package com.example.mall.integration.web;

import com.example.mall.domain.user.UserIdMappingRepository;
import com.example.mall.domain.user.UserLoyalty;
import com.example.mall.domain.user.UserLoyaltyRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * I6: Loyalty query tool. Called by AskFlow's {@code search_loyalty} when the user asks "how many
 * points do I have". Auth: service token. The lookup translates {@code askflow_user_id → mall_user_id}
 * via the mapping table; the loyalty row is created on demand with default values when missing
 * (so brand-new users still get a sensible answer).
 */
@RestController
@RequestMapping("/api/v1/integration/loyalty")
public class LoyaltyController {

    private final UserIdMappingRepository mappingRepository;
    private final UserLoyaltyRepository loyaltyRepository;

    public LoyaltyController(
            UserIdMappingRepository mappingRepository, UserLoyaltyRepository loyaltyRepository) {
        this.mappingRepository = mappingRepository;
        this.loyaltyRepository = loyaltyRepository;
    }

    @GetMapping("/points")
    @Transactional
    public ResponseEntity<LoyaltyResponse> points(
            @RequestParam("askflow_user_id") UUID askflowUserId) {
        return mappingRepository
                .findByAskflowUserId(askflowUserId)
                .map(
                        mapping -> {
                            UserLoyalty row =
                                    loyaltyRepository
                                            .findById(mapping.getMallUserId())
                                            .orElseGet(
                                                    () ->
                                                            loyaltyRepository.save(
                                                                    new UserLoyalty(
                                                                            mapping.getMallUserId())));
                            return ResponseEntity.ok(
                                    new LoyaltyResponse(
                                            mapping.getMallUserId().toString(),
                                            row.getPoints(),
                                            row.getTier().name(),
                                            row.getExpiringSoon()));
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
