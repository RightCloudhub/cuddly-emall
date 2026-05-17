package com.example.mall.web.cart;

import com.example.mall.application.cart.CartService;
import com.example.mall.domain.cart.CartItem;
import com.example.mall.web.security.CurrentUser;
import com.example.mall.web.security.JwtAuthenticationFilter.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart/items")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public List<CartItemResponse> list(@CurrentUser AuthenticatedUser principal) {
        return cartService.listItems(principal.id()).stream()
                .map(CartItemResponse::from)
                .toList();
    }

    @PostMapping
    public CartItemResponse add(
            @CurrentUser AuthenticatedUser principal, @Valid @RequestBody AddItemRequest req) {
        CartItem item = cartService.addItem(principal.id(), req.skuId(), req.qty());
        return CartItemResponse.from(item);
    }

    @PatchMapping("/{id}")
    public CartItemResponse update(
            @CurrentUser AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateItemRequest req) {
        return CartItemResponse.from(
                cartService.updateItemQty(principal.id(), id, req.qty()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @CurrentUser AuthenticatedUser principal, @PathVariable Long id) {
        cartService.removeItem(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    public record AddItemRequest(@NotNull Long skuId, @Min(1) int qty) {}

    public record UpdateItemRequest(@Min(1) int qty) {}

    public record CartItemResponse(Long id, Long skuId, int qty) {
        public static CartItemResponse from(CartItem item) {
            return new CartItemResponse(item.getId(), item.getSkuId(), item.getQty());
        }
    }
}
