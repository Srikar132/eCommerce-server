package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Customization;
import org.hibernate.annotations.processing.Find;
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

    // Find by customization ID
    Optional<Customization> findByCustomizationId(String customizationId);

    // Find user's customizations for a specific product (MOST IMPORTANT)
    @Query("SELECT c FROM Customization c WHERE c.userId = :userId AND c.productId = :productId " +
            "ORDER BY c.updatedAt DESC")
    List<Customization> findByUserIdAndProductId(
            @Param("userId") UUID userId,
            @Param("productId") Long productId
    );

    // Find user's all customizations
    Page<Customization> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    //Find guest's customizations by session
    @Query("SELECT c FROM Customization c WHERE c.sessionId = :sessionId AND c.userId IS NULL " +
            "ORDER BY c.updatedAt DESC")
    List<Customization> findBySessionId(@Param("sessionId") String sessionId);

    //find guest's customizations for a specific product
    @Query("SELECT c FROM Customization c WHERE c.sessionId = :sessionId AND c.productId = :productId " +
            "AND c.userId IS NULL ORDER BY c.updatedAt DESC")
    List<Customization> findBySessionIdAndProductId(
            @Param("sessionId") String sessionId,
            @Param("productId") Long productId
    );

    Long countByUserId(UUID userId);

    // Find incomplete customizations (for cleanup)
    @Query("SELECT c FROM Customization c WHERE c.isCompleted = false " +
            "AND c.lastAccessedAt < :threshold")
    List<Customization> findIncompleteCustomizationsOlderThan(
            @Param("threshold") LocalDateTime threshold
    );

    // find recent customizations
    Optional<Customization> findTopByUserIdAndProductIdOrderByUpdatedAtDesc(UUID userId, UUID productId);

    @Modifying
    @Query("UPDATE Customization c SET c.lastAccessedAt = :accessTime WHERE c.id = :id")
    void updateLastAccessedAt(@Param("id") UUID id, @Param("accessTime") LocalDateTime accessTime);

    // Delete old guest customizations (cleanup job)
    @Modifying
    @Query("DELETE FROM Customization c WHERE c.userId IS NULL " +
            "AND c.createdAt < :threshold")
    void deleteOldGuestCustomizations(@Param("threshold") LocalDateTime threshold);
}
