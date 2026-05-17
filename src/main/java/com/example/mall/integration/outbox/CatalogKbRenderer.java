package com.example.mall.integration.outbox;

import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link Product} (with its variants) into the markdown chunk that AskFlow indexes for
 * RAG. Format matches the prompt.md spec — title H1, description, "## 规格" with bulleted variant
 * lines, "## 退换政策" with the policy snippet.
 */
public final class CatalogKbRenderer {

    private CatalogKbRenderer() {}

    public static String render(Product product, List<ProductVariant> variants) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(product.getTitle()).append("\n\n");
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            sb.append(product.getDescription().trim()).append("\n\n");
        }
        sb.append("## 规格\n");
        if (variants == null || variants.isEmpty()) {
            sb.append("- (暂无规格)\n");
        } else {
            for (ProductVariant v : variants) {
                sb.append("- ").append(v.getSkuCode());
                sb.append("（价格 ").append(formatPrice(v.getPrice())).append(" CNY");
                if (v.getWeightG() > 0) {
                    sb.append("，重量 ").append(v.getWeightG()).append("g");
                }
                if (v.getAttributes() != null && !v.getAttributes().isEmpty()) {
                    sb.append("，属性：").append(renderAttributes(v.getAttributes()));
                }
                sb.append("）\n");
            }
        }
        sb.append("\n## 退换政策\n");
        String policy = product.getPolicySnippet();
        sb.append(policy == null || policy.isBlank() ? "（按平台默认政策处理）" : policy.trim());
        sb.append('\n');
        return sb.toString();
    }

    /** Doc id / source. {@code mall:product:{sku}} per prompt.md. */
    public static String productDocId(String spuCode) {
        return "mall:product:" + spuCode;
    }

    private static String renderAttributes(Map<String, ?> attrs) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, ?> e : attrs.entrySet()) {
            if (!first) sb.append('/');
            sb.append(e.getKey()).append('=').append(String.valueOf(e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private static String formatPrice(BigDecimal price) {
        if (price == null) return "0.00";
        return price.stripTrailingZeros().toPlainString();
    }
}
