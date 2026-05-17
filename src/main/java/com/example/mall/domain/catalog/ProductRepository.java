package com.example.mall.domain.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySpuCode(String spuCode);

    boolean existsBySpuCode(String spuCode);

    Page<Product> findByStatus(Product.Status status, Pageable pageable);

    List<Product> findByCategoryIdAndStatus(Long categoryId, Product.Status status);
}
