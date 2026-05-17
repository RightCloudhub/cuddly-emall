package com.example.mall.application.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mall.application.promotion.CouponApplicationService.CouponQuote;
import com.example.mall.domain.promotion.Coupon;
import com.example.mall.domain.promotion.CouponRepository;
import com.example.mall.domain.promotion.CouponType;
import com.example.mall.web.error.ConflictException;
import com.example.mall.web.error.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponApplicationServiceTest {

    @Mock CouponRepository couponRepository;

    CouponApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CouponApplicationService(couponRepository);
    }

    @Test
    void nullCodeReturnsNoDiscount() {
        CouponQuote q = service.quote(null, new BigDecimal("100"));
        assertThat(q.discount()).isEqualByComparingTo("0");
        assertThat(q.total()).isEqualByComparingTo("100");
        Mockito.verifyNoInteractions(couponRepository);
    }

    @Test
    void unknownCodeIsNotFound() {
        Mockito.when(couponRepository.findByCode("MISSING")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.quote("MISSING", new BigDecimal("10")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void expiredCouponIsRejected() {
        Coupon c =
                new Coupon(
                        "EXPIRED",
                        CouponType.FLAT,
                        new BigDecimal("10"),
                        BigDecimal.ZERO,
                        Instant.now().minus(1, ChronoUnit.DAYS));
        Mockito.when(couponRepository.findByCode("EXPIRED")).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.quote("EXPIRED", new BigDecimal("100")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void subtotalBelowMinIsRejected() {
        Coupon c =
                new Coupon(
                        "OVER100",
                        CouponType.FLAT,
                        new BigDecimal("10"),
                        new BigDecimal("100"),
                        null);
        Mockito.when(couponRepository.findByCode("OVER100")).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.quote("OVER100", new BigDecimal("50")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void flatCouponSubtractsFixedAmount() {
        Coupon c =
                new Coupon(
                        "FLAT20",
                        CouponType.FLAT,
                        new BigDecimal("20"),
                        BigDecimal.ZERO,
                        null);
        setId(c, 7L);
        Mockito.when(couponRepository.findByCode("FLAT20")).thenReturn(Optional.of(c));

        CouponQuote q = service.quote("FLAT20", new BigDecimal("100"));
        assertThat(q.couponId()).isEqualTo(7L);
        assertThat(q.discount()).isEqualByComparingTo("20.0000");
        assertThat(q.total()).isEqualByComparingTo("80.0000");
    }

    @Test
    void percentCouponCalculatesProportion() {
        Coupon c =
                new Coupon(
                        "PCT10",
                        CouponType.PERCENT,
                        new BigDecimal("10"),
                        BigDecimal.ZERO,
                        null);
        Mockito.when(couponRepository.findByCode("PCT10")).thenReturn(Optional.of(c));

        CouponQuote q = service.quote("PCT10", new BigDecimal("250"));
        assertThat(q.discount()).isEqualByComparingTo("25.0000");
        assertThat(q.total()).isEqualByComparingTo("225.0000");
    }

    @Test
    void discountIsCappedAtSubtotal() {
        Coupon c =
                new Coupon(
                        "BIG",
                        CouponType.FLAT,
                        new BigDecimal("500"),
                        BigDecimal.ZERO,
                        null);
        Mockito.when(couponRepository.findByCode("BIG")).thenReturn(Optional.of(c));

        CouponQuote q = service.quote("BIG", new BigDecimal("100"));
        assertThat(q.discount()).isEqualByComparingTo("100");
        assertThat(q.total()).isEqualByComparingTo("0");
    }

    private static void setId(Coupon c, Long id) {
        try {
            var f = Coupon.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
