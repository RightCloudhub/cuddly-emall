package com.example.mall.domain.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartIdOrderByAddedAtAsc(Long cartId);

    Optional<CartItem> findByCartIdAndSkuId(Long cartId, Long skuId);

    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);
}
