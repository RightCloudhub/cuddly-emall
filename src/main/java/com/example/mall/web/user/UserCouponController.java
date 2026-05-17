package com.example.mall.web.user;

import com.example.mall.application.promotion.CouponService;
import com.example.mall.domain.promotion.Coupon;
import com.example.mall.domain.promotion.CouponRepository;
import com.example.mall.domain.promotion.CouponType;
import com.example.mall.domain.promotion.UserCoupon;
import com.example.mall.web.security.CurrentUser;
import com.example.mall.web.security.JwtAuthenticationFilter.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lists the active (unused, unexpired) coupons issued to the current user. */
@RestController
@RequestMapping("/api/v1/users/me/coupons")
public class UserCouponController {

    private final CouponService couponService;
    private final CouponRepository couponRepository;

    public UserCouponController(CouponService couponService, CouponRepository couponRepository) {
        this.couponService = couponService;
        this.couponRepository = couponRepository;
    }

    @GetMapping
    public List<UserCouponResponse> mine(@CurrentUser AuthenticatedUser principal) {
        List<UserCoupon> active = couponService.activeForUser(principal.id());
        if (active.isEmpty()) {
            return List.of();
        }
        Map<Long, Coupon> coupons = new HashMap<>();
        couponRepository
                .findAllById(active.stream().map(UserCoupon::getCouponId).toList())
                .forEach(c -> coupons.put(c.getId(), c));
        return active.stream()
                .map(uc -> UserCouponResponse.from(uc, coupons.get(uc.getCouponId())))
                .filter(r -> r != null && r.enabled() && r.notExpired())
                .toList();
    }

    public record UserCouponResponse(
            Long userCouponId,
            Long couponId,
            String code,
            CouponType type,
            BigDecimal value,
            BigDecimal minTotal,
            Instant expiresAt,
            Instant issuedAt,
            boolean enabled,
            boolean notExpired) {
        static UserCouponResponse from(UserCoupon uc, Coupon c) {
            if (c == null) return null;
            boolean notExpired = c.getExpiresAt() == null || c.getExpiresAt().isAfter(Instant.now());
            return new UserCouponResponse(
                    uc.getId(),
                    c.getId(),
                    c.getCode(),
                    c.getType(),
                    c.getValue(),
                    c.getMinTotal(),
                    c.getExpiresAt(),
                    uc.getIssuedAt(),
                    c.isEnabled(),
                    notExpired);
        }
    }
}
