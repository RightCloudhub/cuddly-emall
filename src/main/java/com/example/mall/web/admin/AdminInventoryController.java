package com.example.mall.web.admin;

import com.example.mall.application.inventory.InventoryService;
import com.example.mall.domain.inventory.Inventory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{skuId}")
    public InventoryResponse get(@PathVariable Long skuId) {
        return InventoryResponse.from(inventoryService.get(skuId));
    }

    @PostMapping("/{skuId}/restock")
    public InventoryResponse restock(
            @PathVariable Long skuId, @Valid @RequestBody RestockRequest req) {
        inventoryService.restock(skuId, req.qty());
        return InventoryResponse.from(inventoryService.get(skuId));
    }

    public record RestockRequest(@Min(1) int qty) {}

    public record InventoryResponse(Long skuId, int available, int reserved) {
        public static InventoryResponse from(Inventory i) {
            return new InventoryResponse(i.getSkuId(), i.getAvailable(), i.getReserved());
        }
    }
}
