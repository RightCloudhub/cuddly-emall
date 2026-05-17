package com.example.mall.application.catalog;

import com.example.mall.domain.catalog.Category;
import com.example.mall.domain.catalog.CategoryRepository;
import com.example.mall.web.error.ConflictException;
import com.example.mall.web.error.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> listAll() {
        return categoryRepository.findAllByOrderBySortAscIdAsc();
    }

    @Transactional
    public Category create(Long parentId, String name, String slug, int sort) {
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("slug already in use");
        }
        if (parentId != null && !categoryRepository.existsById(parentId)) {
            throw new NotFoundException("parent category not found");
        }
        return categoryRepository.save(new Category(parentId, name, slug, sort));
    }

    @Transactional
    public Category update(Long id, String name, String slug, Integer sort) {
        Category c =
                categoryRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("category not found"));
        if (!c.getSlug().equals(slug) && categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("slug already in use");
        }
        c.setName(name);
        c.setSlug(slug);
        if (sort != null) {
            c.setSort(sort);
        }
        return c;
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new NotFoundException("category not found");
        }
        categoryRepository.deleteById(id);
    }
}
