package com.example.mall.application.cart;

import com.example.mall.domain.cart.Cart;
import com.example.mall.domain.cart.CartItem;
import com.example.mall.domain.cart.CartItemRepository;
import com.example.mall.domain.cart.CartRepository;
import com.example.mall.domain.catalog.ProductVariantRepository;
import com.example.mall.web.error.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductVariantRepository variantRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional
    public Cart getOrCreate(Long userId) {
        return cartRepository
                .findByUserId(userId)
                .orElseGet(() -> cartRepository.save(new Cart(userId)));
    }

    @Transactional(readOnly = true)
    public List<CartItem> listItems(Long userId) {
        return cartRepository
                .findByUserId(userId)
                .map(c -> cartItemRepository.findByCartIdOrderByAddedAtAsc(c.getId()))
                .orElseGet(List::of);
    }

    @Transactional
    public CartItem addItem(Long userId, Long skuId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (!variantRepository.existsById(skuId)) {
            throw new NotFoundException("sku not found");
        }
        Cart cart = getOrCreate(userId);
        CartItem item =
                cartItemRepository
                        .findByCartIdAndSkuId(cart.getId(), skuId)
                        .orElse(null);
        if (item == null) {
            item = cartItemRepository.save(new CartItem(cart.getId(), skuId, qty));
        } else {
            item.setQty(item.getQty() + qty);
        }
        cart.touch();
        return item;
    }

    @Transactional
    public CartItem updateItemQty(Long userId, Long itemId, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        Cart cart =
                cartRepository
                        .findByUserId(userId)
                        .orElseThrow(() -> new NotFoundException("cart is empty"));
        CartItem item =
                cartItemRepository
                        .findByIdAndCartId(itemId, cart.getId())
                        .orElseThrow(() -> new NotFoundException("cart item not found"));
        item.setQty(qty);
        cart.touch();
        return item;
    }

    @Transactional
    public void removeItem(Long userId, Long itemId) {
        Cart cart =
                cartRepository
                        .findByUserId(userId)
                        .orElseThrow(() -> new NotFoundException("cart is empty"));
        CartItem item =
                cartItemRepository
                        .findByIdAndCartId(itemId, cart.getId())
                        .orElseThrow(() -> new NotFoundException("cart item not found"));
        cartItemRepository.delete(item);
        cart.touch();
    }
}
