package com.luxesuite.api.repository;

import com.luxesuite.api.model.ProcessedWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedWebhookRepository extends JpaRepository<ProcessedWebhook, Long> {
    Optional<ProcessedWebhook> findByEventIdAndProvider(String eventId, String provider);
}
