package com.example.mall.application.promotion;

import com.example.mall.domain.promotion.Coupon;
import com.example.mall.domain.promotion.CouponRepository;
import com.example.mall.domain.promotion.CouponType;
import com.example.mall.web.error.ConflictException;
import com.example.mall.web.error.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a coupon by code and computes the discount it grants for a given subtotal. Stateless —
 * applying the coupon to an order is the caller's responsibility (so that the order TX writes
 * coupon_id + decrements the user_coupon in one go).
 */
@Service
public class CouponApplicationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CouponRepository couponRepository;

    public CouponApplicationService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public CouponQuote quote(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return CouponQuote.none(subtotal);
        }
        Coupon coupon =
                couponRepository
                        .findByCode(code)
                        .orElseThrow(() -> new NotFoundException("coupon not found: " + code));
        if (!coupon.isActiveAt(Instant.now())) {
            throw new ConflictException("coupon expired or disabled: " + code);
        }
        if (subtotal.compareTo(coupon.getMinTotal()) < 0) {
            throw new ConflictException(
                    "subtotal "
                            + subtotal.toPlainString()
                            + " is below coupon minimum "
                            + coupon.getMinTotal().toPlainString());
        }
        BigDecimal discount = computeDiscount(coupon, subtotal);
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO);
        return new CouponQuote(coupon.getId(), coupon.getCode(), subtotal, discount, total);
    }

    private BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount;
        if (coupon.getType() == CouponType.FLAT) {
            discount = coupon.getValue();
        } else {
            discount =
                    subtotal.multiply(coupon.getValue())
                            .divide(HUNDRED, 4, RoundingMode.HALF_UP);
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        return discount.setScale(4, RoundingMode.HALF_UP);
    }

    public record CouponQuote(
            Long couponId,
            String code,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total) {
        public static CouponQuote none(BigDecimal subtotal) {
            return new CouponQuote(null, null, subtotal, BigDecimal.ZERO, subtotal);
        }
    }
}
