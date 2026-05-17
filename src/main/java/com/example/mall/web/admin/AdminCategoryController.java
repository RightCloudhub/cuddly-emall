package com.example.mall.web.admin;

import com.example.mall.application.catalog.CategoryService;
import com.example.mall.domain.catalog.Category;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public CategoryResponse create(@Valid @RequestBody CategoryRequest req) {
        Category c =
                categoryService.create(
                        req.parentId(), req.name(), req.slug(), req.sort() == null ? 0 : req.sort());
        return CategoryResponse.from(c);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(
            @PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        Category c = categoryService.update(id, req.name(), req.slug(), req.sort());
        return CategoryResponse.from(c);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record CategoryResponse(Long id, Long parentId, String name, String slug, int sort) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSlug(), c.getSort());
        }
    }
}
