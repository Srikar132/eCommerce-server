package com.nala.armoire.repository;

import com.nala.armoire.model.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant> findByProductIdAndIsActiveTrue(UUID productId);

    List<ProductVariant> findByProductId(UUID productId);
    
    /**
     * Fetch variant with images eagerly loaded (for cart mapping)
     */
    @Query("SELECT v FROM ProductVariant v LEFT JOIN FETCH v.images WHERE v.id = :variantId")
    Optional<ProductVariant> findByIdWithImages(@Param("variantId") UUID variantId);
}