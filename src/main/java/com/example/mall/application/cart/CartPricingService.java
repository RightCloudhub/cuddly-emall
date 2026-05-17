package com.example.mall.application.cart;

import com.example.mall.application.promotion.CouponApplicationService;
import com.example.mall.application.promotion.CouponApplicationService.CouponQuote;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.catalog.ProductVariantRepository;
import com.example.mall.web.error.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the same subtotal/discount/total breakdown the order placement TX will see, but without
 * any side effects. Used by the cart's preview endpoint so the UI can show discounts before the
 * user clicks "Place Order".
 */
@Service
public class CartPricingService {

    private final CartService cartService;
    private final ProductVariantRepository variantRepository;
    private final CouponApplicationService couponApplicationService;

    public CartPricingService(
            CartService cartService,
            ProductVariantRepository variantRepository,
            CouponApplicationService couponApplicationService) {
        this.cartService = cartService;
        this.variantRepository = variantRepository;
        this.couponApplicationService = couponApplicationService;
    }

    @Transactional(readOnly = true)
    public CouponQuote preview(Long userId, String couponCode) {
        BigDecimal subtotal = subtotalFor(userId);
        return couponApplicationService.quote(couponCode, subtotal);
    }

    private BigDecimal subtotalFor(Long userId) {
        var items = cartService.listItems(userId);
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Long> skuIds = items.stream().map(i -> i.getSkuId()).toList();
        var variants = variantRepository.findAllById(skuIds);
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var item : items) {
            ProductVariant v =
                    variants.stream()
                            .filter(x -> x.getId().equals(item.getSkuId()))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("sku missing: " + item.getSkuId()));
            subtotal = subtotal.add(v.getPrice().multiply(BigDecimal.valueOf(item.getQty())));
        }
        return subtotal;
    }
}
