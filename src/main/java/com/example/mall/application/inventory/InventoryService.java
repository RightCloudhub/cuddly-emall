package com.example.mall.application.inventory;

import com.example.mall.domain.inventory.Inventory;
import com.example.mall.domain.inventory.InventoryRepository;
import com.example.mall.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void reserve(Long skuId, int qty) {
        Inventory inv = lock(skuId);
        inv.reserve(qty);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void release(Long skuId, int qty) {
        Inventory inv = lock(skuId);
        inv.releaseReservation(qty);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deductReserved(Long skuId, int qty) {
        Inventory inv = lock(skuId);
        inv.deductReserved(qty);
    }

    @Transactional
    public void restock(Long skuId, int qty) {
        Inventory inv = lock(skuId);
        inv.restock(qty);
    }

    @Transactional(readOnly = true)
    public Inventory get(Long skuId) {
        return inventoryRepository
                .findById(skuId)
                .orElseThrow(() -> new NotFoundException("inventory not found for sku " + skuId));
    }

    private Inventory lock(Long skuId) {
        return inventoryRepository
                .findByIdForUpdate(skuId)
                .orElseThrow(() -> new NotFoundException("inventory not found for sku " + skuId));
    }
}
