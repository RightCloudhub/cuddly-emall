package com.example.mall.domain.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderBySortAscIdAsc(Long productId);

    void deleteByProductId(Long productId);
}
