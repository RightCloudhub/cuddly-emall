package com.example.mall.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogKbRendererTest {

    @Test
    void rendersTitleDescriptionSpecsAndPolicy() {
        Product p = new Product("SPU-1", "测试商品", "这是描述", null);
        p.setPolicySnippet("七天无理由退货");

        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("color", "red");
        attrs.put("size", "M");
        ProductVariant v = new ProductVariant(1L, "SKU-1", attrs, new BigDecimal("99.50"), 250);

        String md = CatalogKbRenderer.render(p, List.of(v));

        assertThat(md).startsWith("# 测试商品");
        assertThat(md).contains("这是描述");
        assertThat(md).contains("## 规格");
        assertThat(md).contains("SKU-1");
        assertThat(md).contains("99.5");
        assertThat(md).contains("color=red/size=M");
        assertThat(md).contains("## 退换政策");
        assertThat(md).contains("七天无理由退货");
    }

    @Test
    void emptyVariantsDoesNotBlowUp() {
        Product p = new Product("SPU-2", "Bare", "", null);
        String md = CatalogKbRenderer.render(p, List.of());
        assertThat(md).contains("(暂无规格)");
        assertThat(md).contains("（按平台默认政策处理）");
    }

    @Test
    void productDocIdPrefixed() {
        assertThat(CatalogKbRenderer.productDocId("SPU-X")).isEqualTo("mall:product:SPU-X");
    }
}
