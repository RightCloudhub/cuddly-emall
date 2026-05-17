package com.example.mall.web.catalog;

import com.example.mall.application.catalog.CatalogService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductImageRepository;
import com.example.mall.domain.catalog.ProductRepository;
import com.example.mall.web.error.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CatalogService catalogService;

    public ProductController(
            ProductRepository productRepository,
            ProductImageRepository imageRepository,
            CatalogService catalogService) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Product> products =
                productRepository.findByStatus(
                        Product.Status.PUBLISHED, PageRequest.of(page, size));
        return products.stream()
                .map(
                        p ->
                                ProductResponse.from(
                                        p,
                                        catalogService.variantsOf(p.getId()),
                                        imageRepository.findByProductIdOrderBySortAscIdAsc(p.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        Product product =
                productRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("product not found"));
        if (product.getStatus() != Product.Status.PUBLISHED) {
            throw new NotFoundException("product not found");
        }
        return ProductResponse.from(
                product,
                catalogService.variantsOf(product.getId()),
                imageRepository.findByProductIdOrderBySortAscIdAsc(product.getId()));
    }
}
