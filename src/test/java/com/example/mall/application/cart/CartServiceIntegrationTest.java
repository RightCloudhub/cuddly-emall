package com.example.mall.application.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.application.catalog.CatalogService;
import com.example.mall.application.user.UserRegistrationService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.user.User;
import com.example.mall.support.PostgresBackedTest;
import com.example.mall.web.error.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CartServiceIntegrationTest extends PostgresBackedTest {

    @Autowired CartService cartService;
    @Autowired CatalogService catalogService;
    @Autowired UserRegistrationService userRegistrationService;

    @Test
    void addingExistingSkuAccumulatesQuantity() {
        User user = userRegistrationService.register(unique("u"), unique("u") + "@example.com", "password123");
        Product product = newProduct("SPU-CART-ADD", "SKU-CART-ADD");
        ProductVariant variant = catalogService.variantsOf(product.getId()).get(0);

        cartService.addItem(user.getId(), variant.getId(), 2);
        cartService.addItem(user.getId(), variant.getId(), 3);

        List<?> items = cartService.listItems(user.getId());
        assertThat(items).hasSize(1);
        assertThat(cartService.listItems(user.getId()).get(0).getQty()).isEqualTo(5);
    }

    @Test
    void anotherUserCannotMutateMyCartItem() {
        User alice = userRegistrationService.register(unique("a"), unique("a") + "@example.com", "password123");
        User bob = userRegistrationService.register(unique("b"), unique("b") + "@example.com", "password123");
        Product product = newProduct("SPU-CART-OWN", "SKU-CART-OWN");
        Long skuId = catalogService.variantsOf(product.getId()).get(0).getId();

        var item = cartService.addItem(alice.getId(), skuId, 1);

        assertThatThrownBy(() -> cartService.updateItemQty(bob.getId(), item.getId(), 5))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> cartService.removeItem(bob.getId(), item.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    private Product newProduct(String spu, String sku) {
        return catalogService.createDraft(
                new CreateProductCommand(
                        spu,
                        "T",
                        "",
                        null,
                        "",
                        List.of(new VariantInput(sku, Map.of(), new BigDecimal("1.0000"), 0))));
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }
}
