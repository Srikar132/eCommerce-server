package com.nala.armoire.repository;

import com.nala.armoire.model.entity.DesignCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesignCategoryRepository extends JpaRepository<DesignCategory, UUID> {

    Optional<DesignCategory> findBySlug(String slug);

    List<DesignCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * Get count of designs for a specific category
     */
    @Query("SELECT COUNT(d) FROM Design d WHERE d.category.id = :categoryId")
    Long countDesignsByCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT dc FROM DesignCategory dc " +
            "LEFT JOIN FETCH dc.designs d " +
            "WHERE dc.isActive = true AND d.isActive = true " +
            "ORDER BY dc.displayOrder ASC")
    List<DesignCategory> findAllActiveWithActiveDesigns();

    /**
     * Fetch category with design count using a JOIN query
     */
    @Query("SELECT c FROM DesignCategory c LEFT JOIN FETCH c.designs WHERE c.id = :id")
    Optional<DesignCategory> findByIdWithDesigns(@Param("id") UUID id);
}
