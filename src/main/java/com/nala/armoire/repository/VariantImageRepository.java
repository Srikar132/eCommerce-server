package com.nala.armoire.repository;

import com.nala.armoire.model.entity.ProductImage;
import com.nala.armoire.model.entity.ProductVariant;
import com.nala.armoire.model.entity.VariantImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for VariantImage junction table
 * Handles many-to-many relationship between ProductImage and ProductVariant
 */
@Repository
public interface VariantImageRepository extends JpaRepository<VariantImage, UUID> {

    /**
     * Find all images for a variant (ordered by display order)
     */
    @Query("SELECT vi.productImage FROM VariantImage vi " +
           "WHERE vi.productVariant.id = :variantId " +
           "ORDER BY vi.displayOrder ASC")
    List<ProductImage> findImagesByVariantId(@Param("variantId") UUID variantId);

    /**
     * Find all variants using an image
     */
    @Query("SELECT vi.productVariant FROM VariantImage vi " +
           "WHERE vi.productImage.id = :imageId")
    List<ProductVariant> findVariantsByImageId(@Param("imageId") UUID imageId);

    /**
     * Batch fetch images for multiple variants (avoid N+1)
     * Returns junction entities with eager-loaded images
     */
    @Query("SELECT vi FROM VariantImage vi " +
           "JOIN FETCH vi.productImage " +
           "WHERE vi.productVariant.id IN :variantIds " +
           "ORDER BY vi.displayOrder ASC")
    List<VariantImage> findByVariantIdsWithImages(@Param("variantIds") List<UUID> variantIds);

    /**
     * Delete all variant-image relationships for a specific variant
     */
    void deleteByProductVariantId(UUID variantId);

    /**
     * Delete all variant-image relationships for a specific image
     */
    void deleteByProductImageId(UUID imageId);

    /**
     * Check if an image is used by any variant
     */
    @Query("SELECT COUNT(vi) > 0 FROM VariantImage vi WHERE vi.productImage.id = :imageId")
    boolean isImageUsedByAnyVariant(@Param("imageId") UUID imageId);

    /**
     * Find variant-image relationship
     */
    @Query("SELECT vi FROM VariantImage vi " +
           "WHERE vi.productVariant.id = :variantId " +
           "AND vi.productImage.id = :imageId")
    VariantImage findByVariantIdAndImageId(@Param("variantId") UUID variantId, 
                                           @Param("imageId") UUID imageId);
}
