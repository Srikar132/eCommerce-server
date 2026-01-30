package com.nala.armoire.repository;

import com.nala.armoire.model.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * UPDATED: Now uses VariantImage junction table
     */
    @Query("SELECT DISTINCT v FROM ProductVariant v " +
           "LEFT JOIN FETCH v.variantImages vi " +
           "LEFT JOIN FETCH vi.productImage " +
           "WHERE v.id = :variantId")
    Optional<ProductVariant> findByIdWithImages(@Param("variantId") UUID variantId);

    /**
     * Fetch all variants for a product with images eagerly loaded (for admin)
     * UPDATED: Now uses VariantImage junction table
     * DISTINCT prevents duplicate rows when joining with images collection
     * This prevents lazy loading issues during serialization
     */
    @Query("SELECT DISTINCT v FROM ProductVariant v " +
            "LEFT JOIN FETCH v.variantImages vi " +
            "LEFT JOIN FETCH vi.productImage " +
            "WHERE v.product.id = :productId " +
            "ORDER BY v.createdAt DESC")
    List<ProductVariant> findByProductIdWithImages(@Param("productId") UUID productId);

    /**
     * Batch fetch variants with images for multiple variant IDs
     * Used to avoid N+1 queries when loading multiple variants
     */
    @Query("SELECT DISTINCT v FROM ProductVariant v " +
           "LEFT JOIN FETCH v.variantImages vi " +
           "LEFT JOIN FETCH vi.productImage " +
           "WHERE v.id IN :variantIds")
    List<ProductVariant> findByIdsWithImages(@Param("variantIds") List<UUID> variantIds);

    /**
     * Bug Fix #14: Pessimistic write lock for stock validation during order creation
     * Prevents race conditions when multiple users order the last item simultaneously
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id")
    Optional<ProductVariant> findByIdWithLock(@Param("id") UUID id);
}