package com.example.mall.web.admin;

import com.example.mall.application.catalog.CatalogCommands;
import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.UpdateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.application.catalog.CatalogService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductImage;
import com.example.mall.domain.catalog.ProductImageRepository;
import com.example.mall.infrastructure.storage.ImageStorageService;
import com.example.mall.web.catalog.ProductResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

    private final CatalogService catalogService;
    private final ProductImageRepository imageRepository;
    private final ImageStorageService imageStorage;

    public AdminProductController(
            CatalogService catalogService,
            ProductImageRepository imageRepository,
            ImageStorageService imageStorage) {
        this.catalogService = catalogService;
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
    }

    @PostMapping
    public ProductResponse create(@Valid @RequestBody CreateProductRequest req) {
        CreateProductCommand cmd =
                new CreateProductCommand(
                        req.spuCode(),
                        req.title(),
                        nullSafe(req.description()),
                        req.categoryId(),
                        nullSafe(req.policySnippet()),
                        toInputs(req.variants()));
        Product product = catalogService.createDraft(cmd);
        return responseFor(product);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable Long id, @Valid @RequestBody UpdateProductRequest req) {
        UpdateProductCommand cmd =
                new UpdateProductCommand(
                        req.title(),
                        nullSafe(req.description()),
                        req.categoryId(),
                        nullSafe(req.policySnippet()),
                        toInputs(req.variants()));
        Product product = catalogService.update(id, cmd);
        return responseFor(product);
    }

    @PostMapping("/{id}/publish")
    public ProductResponse publish(@PathVariable Long id) {
        return responseFor(catalogService.publish(id));
    }

    @PostMapping("/{id}/delist")
    public ProductResponse delist(@PathVariable Long id) {
        return responseFor(catalogService.delist(id));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return responseFor(catalogService.getRequired(id));
    }

    @PostMapping(path = "/{id}/images", consumes = "multipart/form-data")
    public ResponseEntity<ProductImageResponse> uploadImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "0") int sort) {
        Product product = catalogService.getRequired(id);
        String url = imageStorage.upload(file);
        ProductImage image = imageRepository.save(new ProductImage(product.getId(), url, sort));
        return ResponseEntity.ok(
                new ProductImageResponse(image.getId(), image.getUrl(), image.getSort()));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        ProductImage img = imageRepository.findById(imageId).orElse(null);
        if (img == null || !img.getProductId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        imageRepository.delete(img);
        return ResponseEntity.noContent().build();
    }

    private ProductResponse responseFor(Product product) {
        return ProductResponse.from(
                product,
                catalogService.variantsOf(product.getId()),
                imageRepository.findByProductIdOrderBySortAscIdAsc(product.getId()));
    }

    private static List<VariantInput> toInputs(List<VariantPayload> payloads) {
        return payloads.stream()
                .map(
                        p ->
                                new CatalogCommands.VariantInput(
                                        p.skuCode(), p.attributes(), p.price(), p.weightG()))
                .toList();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public record ProductImageResponse(Long id, String url, int sort) {}
}
