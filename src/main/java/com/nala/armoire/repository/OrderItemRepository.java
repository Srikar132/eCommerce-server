package com.nala.armoire.repository;

import com.nala.armoire.model.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    /**
     * Check if user has purchased a specific product
     */
    @Query("""
        SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END
        FROM OrderItem oi
        JOIN oi.order o
        WHERE o.user.id = :userId 
        AND oi.productVariant.product.id = :productId
        AND o.status IN ('DELIVERED')
    """)
    boolean existsByUserIdAndProductId(
            @Param("userId") UUID userId, 
            @Param("productId") UUID productId
    );

    /**
     * Find best selling product IDs
<<<<<<< HEAD
     * 
     * Returns products ordered by total quantity sold (descending)
     * Optionally filtered by category (supports hierarchical categories)
     * Only includes completed/delivered orders
     * 
=======
     *
     * Returns products ordered by total quantity sold (descending)
     * Optionally filtered by category (supports hierarchical categories)
     * Only includes completed/delivered orders
     *
>>>>>>> 80d12ff3fd705cd0a34eae61921a646c767e2f82
     * @param categorySlug Optional category filter (null = all categories)
     * @param pageable Pagination (use for limit)
     */
    @Query("""
        SELECT oi.productVariant.product.id
        FROM OrderItem oi
        JOIN oi.order o
        WHERE o.status IN ('DELIVERED')
        AND oi.productVariant.product.isActive = true
        AND oi.productVariant.product.isDraft = false
        AND (:categorySlug IS NULL 
             OR oi.productVariant.product.category.slug = :categorySlug
             OR oi.productVariant.product.category.parent.slug = :categorySlug
             OR oi.productVariant.product.category.parent.parent.slug = :categorySlug)
        GROUP BY oi.productVariant.product.id
        ORDER BY SUM(oi.quantity) DESC
    """)
    List<UUID> findBestSellingProductIds(
            @Param("categorySlug") String categorySlug,
            Pageable pageable
    );

    /**
     * Find categories that a user has purchased from
<<<<<<< HEAD
     * 
     * Returns category slugs ordered by purchase frequency
     * Only includes completed/delivered orders
     * 
=======
     *
     * Returns category slugs ordered by purchase frequency
     * Only includes completed/delivered orders
     *
>>>>>>> 80d12ff3fd705cd0a34eae61921a646c767e2f82
     * @param userId User ID
     */
    @Query("""
        SELECT DISTINCT oi.productVariant.product.category.slug
        FROM OrderItem oi
        JOIN oi.order o
        WHERE o.user.id = :userId
        AND o.status IN ('DELIVERED')
        AND oi.productVariant.product.category IS NOT NULL
        ORDER BY COUNT(oi.id) DESC
    """)
    List<String> findCategoriesByUserId(@Param("userId") UUID userId);

    /**
     * Find products frequently bought together with a given product
<<<<<<< HEAD
     * 
     * Finds products that appear in the same orders as the specified product
     * Useful for "Customers who bought this also bought" recommendations
     * 
=======
     *
     * Finds products that appear in the same orders as the specified product
     * Useful for "Customers who bought this also bought" recommendations
     *
>>>>>>> 80d12ff3fd705cd0a34eae61921a646c767e2f82
     * @param productId The reference product ID
     * @param pageable Pagination (use for limit)
     */
    @Query("""
        SELECT oi2.productVariant.product.id
        FROM OrderItem oi1
        JOIN OrderItem oi2 ON oi1.order.id = oi2.order.id
        WHERE oi1.productVariant.product.id = :productId
        AND oi2.productVariant.product.id != :productId
        AND oi2.productVariant.product.isActive = true
        AND oi2.productVariant.product.isDraft = false
        AND oi1.order.status IN ('DELIVERED')
        GROUP BY oi2.productVariant.product.id
        ORDER BY COUNT(oi2.id) DESC
    """)
    List<UUID> findFrequentlyBoughtTogether(
            @Param("productId") UUID productId,
            Pageable pageable
    );

    /**
     * Count distinct orders containing a specific product
     * OPTIMIZED: Direct join to product instead of through variant
     * 
     * @param productId The product ID
     * @return Number of distinct orders containing this product
     */
    @Query("""
        SELECT COUNT(DISTINCT oi.order.id)
        FROM OrderItem oi
        JOIN oi.productVariant pv
        WHERE pv.product.id = :productId
    """)
    Long countDistinctOrdersByProductId(@Param("productId") UUID productId);

    /**
     * Batch count orders for multiple products
     * OPTIMIZED: Single query instead of N queries
     * 
     * @param productIds List of product IDs
     * @return Map of productId -> order count
     */
    @Query("""
        SELECT pv.product.id as productId, COUNT(DISTINCT oi.order.id) as orderCount
        FROM OrderItem oi
        JOIN oi.productVariant pv
        WHERE pv.product.id IN :productIds
        GROUP BY pv.product.id
    """)
    List<Object[]> countOrdersByProductIds(@Param("productIds") List<UUID> productIds);
}