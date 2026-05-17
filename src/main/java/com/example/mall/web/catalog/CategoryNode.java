package com.example.mall.web.catalog;

import com.example.mall.domain.catalog.Category;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record CategoryNode(
        Long id, Long parentId, String name, String slug, int sort, List<CategoryNode> children) {

    public static List<CategoryNode> tree(List<Category> all) {
        Map<Long, CategoryNode> byId = new HashMap<>();
        List<CategoryNode> roots = new ArrayList<>();
        for (Category c : all) {
            byId.put(
                    c.getId(),
                    new CategoryNode(c.getId(), c.getParentId(), c.getName(), c.getSlug(), c.getSort(),
                            new ArrayList<>()));
        }
        for (Category c : all) {
            CategoryNode node = byId.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(node);
            } else {
                CategoryNode parent = byId.get(c.getParentId());
                if (parent != null) {
                    parent.children().add(node);
                } else {
                    // orphaned: surface as root rather than dropping
                    roots.add(node);
                }
            }
        }
        return roots;
    }
}
