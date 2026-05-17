package com.example.mall.application.promotion;

import com.example.mall.domain.promotion.Coupon;
import com.example.mall.domain.promotion.CouponRepository;
import com.example.mall.domain.promotion.CouponType;
import com.example.mall.domain.promotion.UserCoupon;
import com.example.mall.domain.promotion.UserCouponRepository;
import com.example.mall.web.error.ConflictException;
import com.example.mall.web.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public CouponService(
            CouponRepository couponRepository, UserCouponRepository userCouponRepository) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
    }

    @Transactional
    public Coupon create(
            String code, CouponType type, BigDecimal value, BigDecimal minTotal, Instant expiresAt) {
        if (couponRepository.existsByCode(code)) {
            throw new ConflictException("coupon code already in use: " + code);
        }
        if (type == CouponType.PERCENT) {
            if (value.signum() <= 0 || value.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(
                        "PERCENT coupon value must be in (0, 100]");
            }
        }
        return couponRepository.save(new Coupon(code, type, value, minTotal, expiresAt));
    }

    @Transactional
    public UserCoupon issueToUser(Long userId, String code) {
        Coupon coupon =
                couponRepository
                        .findByCode(code)
                        .orElseThrow(() -> new NotFoundException("coupon not found: " + code));
        return userCouponRepository
                .findByUserIdAndCouponId(userId, coupon.getId())
                .orElseGet(() -> userCouponRepository.save(new UserCoupon(userId, coupon.getId())));
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> activeForUser(Long userId) {
        return userCouponRepository.findByUserIdAndUsedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public List<Coupon> list() {
        return couponRepository.findAll();
    }
}
