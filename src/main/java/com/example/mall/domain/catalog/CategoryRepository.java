package com.example.mall.domain.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsBySlug(String slug);

    List<Category> findAllByOrderBySortAscIdAsc();
}
