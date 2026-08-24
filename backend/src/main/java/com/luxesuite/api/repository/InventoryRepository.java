package com.luxesuite.api.repository;

import com.luxesuite.api.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductIdAndBranchId(Long productId, Long branchId);
    
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.branch.id = :branchId")
    Optional<Inventory> findByProductIdAndBranchIdForUpdate(@org.springframework.data.repository.query.Param("productId") Long productId, @org.springframework.data.repository.query.Param("branchId") Long branchId);
    java.util.List<Inventory> findByBranchId(Long branchId);
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"product", "branch"})
    org.springframework.data.domain.Page<Inventory> findByBranchId(Long branchId, org.springframework.data.domain.Pageable pageable);
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"product", "branch"})
    org.springframework.data.domain.Page<Inventory> findAll(org.springframework.data.domain.Pageable pageable);
}
