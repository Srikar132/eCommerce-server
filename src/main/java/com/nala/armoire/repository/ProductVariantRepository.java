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

    /**
     * Find active variants for a product (for public/customer use)
     */
    List<ProductVariant> findByProductIdAndIsActiveTrue(UUID productId);

    /**
     * Find all variants for a product (basic - may cause lazy loading issues)
     */
    List<ProductVariant> findByProductId(UUID productId);

    /**
     * Fetch variant with images eagerly loaded (for cart mapping)
     */
    @Query("SELECT v FROM ProductVariant v LEFT JOIN FETCH v.images WHERE v.id = :variantId")
    Optional<ProductVariant> findByIdWithImages(@Param("variantId") UUID variantId);

    /**
     * Fetch all variants for a product with images eagerly loaded (for admin)
     * DISTINCT prevents duplicate rows when joining with images collection
     * This prevents lazy loading issues during serialization
     */
    @Query("SELECT DISTINCT v FROM ProductVariant v " +
            "LEFT JOIN FETCH v.images " +
            "WHERE v.product.id = :productId " +
            "ORDER BY v.createdAt DESC")
    List<ProductVariant> findByProductIdWithImages(@Param("productId") UUID productId);
}