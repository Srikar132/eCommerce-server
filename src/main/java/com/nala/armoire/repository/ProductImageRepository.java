package com.nala.armoire.repository;

import com.nala.armoire.model.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for ProductImage entity
 * UPDATED: Images now belong to Product, not Variant
 */
@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    /**
     * Find all images for a product (ordered by display order)
     */
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(UUID productId);

    /**
     * Find primary image for a product
     */
    @Query("SELECT pi FROM ProductImage pi " +
           "WHERE pi.product.id = :productId " +
           "AND pi.isPrimary = true")
    ProductImage findPrimaryImageByProductId(@Param("productId") UUID productId);

    /**
     * Batch fetch images for multiple products (avoid N+1)
     */
    @Query("SELECT pi FROM ProductImage pi " +
           "WHERE pi.product.id IN :productIds " +
           "ORDER BY pi.displayOrder ASC")
    List<ProductImage> findByProductIds(@Param("productIds") List<UUID> productIds);
}
