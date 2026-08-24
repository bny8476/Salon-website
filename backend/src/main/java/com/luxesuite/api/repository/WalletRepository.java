package com.luxesuite.api.repository;

import com.luxesuite.api.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByCustomerId(Long customerId);
    
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT w FROM Wallet w WHERE w.customer.id = :customerId")
    Optional<Wallet> findByCustomerIdForUpdate(@org.springframework.data.repository.query.Param("customerId") Long customerId);
}
