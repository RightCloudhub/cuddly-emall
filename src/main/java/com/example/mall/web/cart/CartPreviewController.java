package com.example.mall.web.cart;

import com.example.mall.application.cart.CartPricingService;
import com.example.mall.application.promotion.CouponApplicationService.CouponQuote;
import com.example.mall.web.security.CurrentUser;
import com.example.mall.web.security.JwtAuthenticationFilter.AuthenticatedUser;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the discount preview for the current user's cart. Useful for showing the user what their
 * total will be before they click "Place Order".
 */
@RestController
@RequestMapping("/api/v1/cart")
public class CartPreviewController {

    private final CartPricingService cartPricingService;

    public CartPreviewController(CartPricingService cartPricingService) {
        this.cartPricingService = cartPricingService;
    }

    @GetMapping("/preview")
    public PreviewResponse preview(
            @CurrentUser AuthenticatedUser principal,
            @RequestParam(value = "code", required = false) String code) {
        CouponQuote quote = cartPricingService.preview(principal.id(), code);
        return new PreviewResponse(
                quote.subtotal(), quote.discount(), quote.total(), quote.code(), quote.couponId());
    }

    public record PreviewResponse(
            BigDecimal subtotal, BigDecimal discount, BigDecimal total, String code, Long couponId) {}
}
