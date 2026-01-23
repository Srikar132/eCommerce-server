package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Design;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesignRepository extends JpaRepository<Design, UUID>, JpaSpecificationExecutor<Design> {
    // Find by ID with category (single query with join)
    @Query("SELECT d FROM Design d JOIN FETCH d.category WHERE d.id = :id")
    Optional<Design> findByIdWithCategory(@Param("id") UUID id);

    //find all active designs
    Page<Design> findByIsActiveTrue(Pageable pageable);

    //find designs by a category
    @Query("SELECT d FROM Design d WHERE d.category.id = :categoryId AND d.isActive = true")
    Page<Design> findByCategoryId(@Param("categoryId") UUID categoryId , Pageable pageable);

    //find designs by category slug
    @Query("SELECT d FROM Design d WHERE d.category.slug = :slug AND d.isActive = true")
    Page<Design> findByCategorySlug(@Param("slug") String slug, Pageable pageable);

    //search designs by name or tags
    @Query("SELECT d FROM Design d WHERE d.isActive = true AND " +
            "(LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Design> searchDesigns(@Param("searchTerm") String searchTerm, Pageable pageable);

    //search designs by category slug and search term
    @Query("SELECT d FROM Design d WHERE d.isActive = true " +
            "AND d.category.slug = :categorySlug " +
            "AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
            "OR LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
            "OR LOWER(d.description) LIKE LOWER(CONCAT('%', :searchQuery, '%')))")
    Page<Design> searchDesignsByCategorySlugAndQuery(
            @Param("categorySlug") String categorySlug,
            @Param("searchQuery") String searchQuery,
            Pageable pageable
    );

    // Find designs with filters (removed product category compatibility check)
    @Query("SELECT d FROM Design d WHERE d.isActive = true " +
            "AND (:categoryId IS NULL OR d.category.id = :categoryId) " +
            "AND (:searchTerm IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Design> findWithFilters(
            @Param("categoryId") Long categoryId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );

    // Get designs by IDs (for batch operations)
    @Query("SELECT d FROM Design d JOIN FETCH d.category WHERE d.id IN :ids AND d.isActive = true")
    List<Design> findAllByIdInWithCategory(@Param("ids") List<UUID> ids);
}
