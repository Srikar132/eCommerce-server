package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Customization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomizationRepository extends JpaRepository<Customization, UUID> {

    // Find user's customizations for a specific product
    @Query("SELECT c FROM Customization c WHERE c.userId = :userId AND c.productId = :productId " +
            "ORDER BY c.updatedAt DESC")
    List<Customization> findByUserIdAndProductId(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId
    );

    // Find user's all customizations
    Page<Customization> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    Long countByUserId(UUID userId);

    // Find incomplete customizations (for cleanup)
    @Query("SELECT c FROM Customization c WHERE c.isCompleted = false " +
            "AND c.createdAt < :threshold")
    List<Customization> findIncompleteCustomizationsOlderThan(
            @Param("threshold") LocalDateTime threshold
    );

    // Find recent customization
    Optional<Customization> findTopByUserIdAndProductIdOrderByUpdatedAtDesc(UUID userId, UUID productId);
}

