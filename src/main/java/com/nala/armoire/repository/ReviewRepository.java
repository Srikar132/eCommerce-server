package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByProductId(UUID productId, Pageable pageable);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId")
    Long countByProductId(@Param("productId") UUID productId);

    /**
     * Batch get average ratings for multiple products
     * OPTIMIZED: Single query instead of N queries
     * 
     * @param productIds List of product IDs
     * @return Map of productId -> average rating
     */
    @Query("SELECT r.product.id as productId, AVG(r.rating) as avgRating " +
           "FROM Review r WHERE r.product.id IN :productIds GROUP BY r.product.id")
    List<Object[]> findAverageRatingsByProductIds(@Param("productIds") List<UUID> productIds);

    /**
     * Batch count reviews for multiple products
     * OPTIMIZED: Single query instead of N queries
     * 
     * @param productIds List of product IDs
     * @return Map of productId -> review count
     */
    @Query("SELECT r.product.id as productId, COUNT(r) as reviewCount " +
           "FROM Review r WHERE r.product.id IN :productIds GROUP BY r.product.id")
    List<Object[]> countReviewsByProductIds(@Param("productIds") List<UUID> productIds);
}
