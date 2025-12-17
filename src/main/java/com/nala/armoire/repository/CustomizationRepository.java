package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Customization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomizationRepository extends JpaRepository<Customization, UUID> {

    /**
     * Find customization by unique customization ID
     * Used for: Direct customization lookup by ID
     */
    Optional<Customization> findByCustomizationId(String customizationId);

    /**
     * Find all customizations for a user and specific product (ordered by most recent)
     * Used for: Loading previous designs in customizer, product customization history
     */
    @Query("SELECT c FROM Customization c WHERE c.userId = :userId AND c.productId = :productId " +
            "ORDER BY c.updatedAt DESC")
    List<Customization> findByUserIdAndProductId(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId
    );

    /**
     * Find all customizations for a user (paginated, ordered by most recent)
     * Used for: "My Designs" page
     */
    Page<Customization> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find guest customizations by session ID
     * Used for: Loading guest designs before login
     */
    @Query("SELECT c FROM Customization c WHERE c.sessionId = :sessionId AND c.userId IS NULL " +
            "ORDER BY c.updatedAt DESC")
    List<Customization> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * Find guest customizations for a specific product
     * Used for: Loading previous guest designs for specific product
     */
    @Query("SELECT c FROM Customization c WHERE c.sessionId = :sessionId AND c.productId = :productId " +
            "AND c.userId IS NULL ORDER BY c.updatedAt DESC")
    List<Customization> findBySessionIdAndProductId(
            @Param("sessionId") String sessionId,
            @Param("productId") UUID productId
    );

    /**
     * Get most recent customization for a user and product
     * Used for: Auto-loading last design in customizer
     */
    Optional<Customization> findTopByUserIdAndProductIdOrderByUpdatedAtDesc(
            UUID userId,
            UUID productId
    );

    /**
     * Count total customizations for a user
     * Used for: User statistics, dashboard metrics
     */
    Long countByUserId(UUID userId);

    /**
     * Find incomplete customizations older than threshold
     * Used for: Cleanup jobs to remove abandoned designs
     */
    @Query("SELECT c FROM Customization c WHERE c.isCompleted = false " +
            "AND c.lastAccessedAt < :threshold")
    List<Customization> findIncompleteCustomizationsOlderThan(
            @Param("threshold") LocalDateTime threshold
    );

    /**
     * Update last accessed time for a customization
     * Used for: Tracking when customization was last viewed/edited
     */
    @Modifying
    @Query("UPDATE Customization c SET c.lastAccessedAt = :accessTime WHERE c.id = :id")
    void updateLastAccessedAt(
            @Param("id") UUID id,
            @Param("accessTime") LocalDateTime accessTime
    );

    /**
     * Delete old guest customizations (cleanup)
     * Used for: Scheduled job to remove old guest designs
     */
    @Modifying
    @Query("DELETE FROM Customization c WHERE c.userId IS NULL " +
            "AND c.createdAt < :threshold")
    void deleteOldGuestCustomizations(@Param("threshold") LocalDateTime threshold);

    /**
     * Check if user has any customizations for a product
     * Used for: Quick check if user has saved designs
     */
    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    /**
     * Find customizations by multiple IDs (for batch operations)
     * Used for: Bulk operations like order creation with multiple customizations
     */
    @Query("SELECT c FROM Customization c WHERE c.customizationId IN :customizationIds")
    List<Customization> findByCustomizationIdIn(@Param("customizationIds") List<String> customizationIds);
}