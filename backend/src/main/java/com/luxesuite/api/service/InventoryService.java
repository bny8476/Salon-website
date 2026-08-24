package com.luxesuite.api.service;

import com.luxesuite.api.dto.InventoryDto;
import com.luxesuite.api.exception.ConflictException;
import com.luxesuite.api.exception.ResourceNotFoundException;
import com.luxesuite.api.model.Branch;
import com.luxesuite.api.model.Inventory;
import com.luxesuite.api.model.Product;
import com.luxesuite.api.repository.BranchRepository;
import com.luxesuite.api.repository.InventoryRepository;
import com.luxesuite.api.repository.ProductRepository;
import com.luxesuite.api.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public com.luxesuite.api.dto.PageResponse<InventoryDto> getAllInventory(int page, int size) {
        org.springframework.data.domain.Page<Inventory> inventoryPage = inventoryRepository.findAll(org.springframework.data.domain.PageRequest.of(page, size));
        return com.luxesuite.api.dto.PageResponse.of(inventoryPage.map(this::mapToDto));
    }

    @Transactional(readOnly = true)
    public com.luxesuite.api.dto.PageResponse<InventoryDto> getInventoryByBranch(Long branchId, int page, int size) {
        securityUtils.validateBranchAccess(branchId);
        org.springframework.data.domain.Page<Inventory> inventoryPage = inventoryRepository.findByBranchId(branchId, org.springframework.data.domain.PageRequest.of(page, size));
        return com.luxesuite.api.dto.PageResponse.of(inventoryPage.map(this::mapToDto));
    }

    @Transactional(readOnly = true)
    public InventoryDto getInventoryById(Long id) {
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
        return mapToDto(inventory);
    }

    @Transactional
    public InventoryDto updateInventory(Long id, InventoryDto dto) {
        Inventory existing = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found"));
                
        securityUtils.validateBranchAccess(existing.getBranch().getId());
        return mapToDto(inventoryRepository.save(existing));
    }

    @Transactional
    public InventoryDto updateStock(Long productId, Long branchId, int quantityAdjustment) {
        securityUtils.validateBranchAccess(branchId);

        Inventory inventory = inventoryRepository.findByProductIdAndBranchIdForUpdate(productId, branchId)
                .orElseGet(() -> createNewInventory(productId, branchId));
        
        int newQuantity = inventory.getQuantity() + quantityAdjustment;
        if (newQuantity < 0) {
            throw new ConflictException("Insufficient stock for product " + inventory.getProduct().getName());
        }
        
        inventory.setQuantity(newQuantity);
        
        if (newQuantity <= (inventory.getReorderLevel() != null ? inventory.getReorderLevel() : 0)) {
            // In a real app, this would trigger NotificationService
            log.warn("LOW STOCK ALERT: {} at branch {}", inventory.getProduct().getName(), inventory.getBranch().getName());
        }
        
        return mapToDto(inventoryRepository.save(inventory));
    }

    private Inventory createNewInventory(Long productId, Long branchId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
        
        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setBranch(branch);
        inventory.setQuantity(0);
        inventory.setReorderLevel(5);
        return inventory;
    }

    private InventoryDto mapToDto(Inventory inventory) {
        InventoryDto dto = new InventoryDto();
        dto.setId(inventory.getId());
        dto.setProductId(inventory.getProduct().getId());
        dto.setBranchId(inventory.getBranch().getId());
        dto.setQuantity(inventory.getQuantity());
        dto.setLowStockThreshold(inventory.getReorderLevel());
        return dto;
    }
}
