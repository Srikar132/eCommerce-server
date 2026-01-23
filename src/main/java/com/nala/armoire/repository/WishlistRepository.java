package com.nala.armoire.repository;

import com.nala.armoire.model.entity.WishList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishList, UUID> {

    /**
     * Find all wishlist items for a specific user
     */
    @Query("SELECT w FROM WishList w " +
           "JOIN FETCH w.product p " +
           "LEFT JOIN FETCH p.brand " +
           "LEFT JOIN FETCH p.category " +
           "WHERE w.user.id = :userId " +
           "ORDER BY w.createdAt DESC")
    List<WishList> findByUserIdWithProduct(@Param("userId") UUID userId);

    /**
     * Find a specific wishlist item by user and product
     */
    @Query("SELECT w FROM WishList w " +
           "WHERE w.user.id = :userId AND w.product.id = :productId")
    Optional<WishList> findByUserIdAndProductId(
        @Param("userId") UUID userId,
        @Param("productId") UUID productId
    );

    /**
     * Check if a product exists in user's wishlist
     */
    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    /**
     * Count wishlist items for a user
     */
    long countByUserId(UUID userId);

    /**
     * Delete a wishlist item by user and product
     */
    void deleteByUserIdAndProductId(UUID userId, UUID productId);

    /**
     * Delete all wishlist items for a user
     */
    void deleteByUserId(UUID userId);
}
