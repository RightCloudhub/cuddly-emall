package com.example.mall.web.admin;

import com.example.mall.application.promotion.CouponService;
import com.example.mall.domain.promotion.Coupon;
import com.example.mall.domain.promotion.CouponType;
import com.example.mall.domain.promotion.UserCoupon;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/coupons")
public class AdminCouponController {

    private final CouponService couponService;

    public AdminCouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    public List<CouponResponse> list() {
        return couponService.list().stream().map(CouponResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CreateCouponRequest req) {
        Coupon c =
                couponService.create(
                        req.code(),
                        req.type(),
                        req.value(),
                        req.minTotal() == null ? BigDecimal.ZERO : req.minTotal(),
                        req.expiresAt());
        return CouponResponse.from(c);
    }

    @PostMapping("/{code}/issue")
    public IssueResponse issue(@PathVariable String code, @Valid @RequestBody IssueRequest req) {
        UserCoupon uc = couponService.issueToUser(req.userId(), code);
        return new IssueResponse(uc.getId(), uc.getUserId(), uc.getCouponId(), uc.getIssuedAt());
    }

    public record CreateCouponRequest(
            @NotBlank @Size(max = 32) String code,
            @NotNull CouponType type,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal value,
            BigDecimal minTotal,
            Instant expiresAt) {}

    public record IssueRequest(@NotNull Long userId) {}

    public record CouponResponse(
            Long id,
            String code,
            CouponType type,
            BigDecimal value,
            BigDecimal minTotal,
            Instant expiresAt,
            boolean enabled) {
        public static CouponResponse from(Coupon c) {
            return new CouponResponse(
                    c.getId(),
                    c.getCode(),
                    c.getType(),
                    c.getValue(),
                    c.getMinTotal(),
                    c.getExpiresAt(),
                    c.isEnabled());
        }
    }

    public record IssueResponse(Long id, Long userId, Long couponId, Instant issuedAt) {}
}
