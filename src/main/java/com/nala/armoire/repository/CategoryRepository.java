package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // ==================== Existing Methods (Preserved) ====================

    Optional<Category> findBySlugAndIsActiveTrue(String slug);

    Optional<Category> findBySlug(String slug);

    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<Category> findAllByOrderByDisplayOrderAsc();

    boolean existsBySlug(String slug);

    List<Category> findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();

    List<Category> findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID parentId);

    List<Category> findByParentIsNullOrderByDisplayOrderAsc();

    List<Category> findByParentIdOrderByDisplayOrderAsc(UUID parentId);

    boolean existsByParentId(UUID parentId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.isActive = true ORDER BY c.displayOrder ASC")
    List<Category> findAllActiveWithParent();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent ORDER BY c.displayOrder ASC")
    List<Category> findAllWithParent();

    /**
     * RECURSIVE product count - counts products from category AND all its descendants
     * - For root categories (Men): counts all products in Men + Topwear + Casual Shirts + T-Shirts, etc.
     * - For parent categories (Topwear): counts all products in Topwear + Casual Shirts + T-Shirts, etc.
     * - For leaf categories (Casual Shirts): counts only products directly in Casual Shirts
     */
    @Query(value = """
        WITH RECURSIVE category_tree AS (
            SELECT id FROM categories WHERE id = :categoryId
            UNION ALL
            SELECT c.id FROM categories c
            INNER JOIN category_tree ct ON c.parent_id = ct.id
        )
        SELECT COUNT(p.id) 
        FROM products p
        INNER JOIN category_tree ct ON p.category_id = ct.id
        WHERE p.is_active = true
        """, nativeQuery = true)
    Long countProductsByCategoryId(@Param("categoryId") UUID categoryId);

    @Query(value = "WITH RECURSIVE category_tree AS (" +
            "SELECT id, parent_id FROM categories WHERE id = :categoryId " +
            "UNION ALL " +
            "SELECT c.id, c.parent_id FROM categories c " +
            "INNER JOIN category_tree ct ON c.parent_id = ct.id) " +
            "SELECT id FROM category_tree WHERE id != :categoryId", nativeQuery = true)
    List<UUID> findAllDescendantIds(@Param("categoryId") UUID categoryId);

    @Query("SELECT COALESCE(MAX(c.displayOrder), 0) FROM Category c WHERE (:parentId IS NULL AND c.parent IS NULL) OR (c.parent.id = :parentId)")
    int findMaxDisplayOrderByParent(@Param("parentId") UUID parentId);

    // ==================== New Production-Grade Methods ====================

    // ===== Enhanced Find with Parent (Prevent N+1 Queries) =====

    @EntityGraph(attributePaths = {"parent", "parent.parent"})
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL")
    List<Category> findRootCategoriesWithParent();

    @EntityGraph(attributePaths = {"parent", "parent.parent"})
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.isActive = true")
    List<Category> findActiveRootCategoriesWithParent();

    @EntityGraph(attributePaths = {"parent", "parent.parent"})
    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId")
    List<Category> findByParentIdWithParent(@Param("parentId") UUID parentId);

    @EntityGraph(attributePaths = {"parent", "parent.parent"})
    @Query("SELECT c FROM Category c WHERE c.slug = :slug")
    Optional<Category> findBySlugWithParent(@Param("slug") String slug);

    // ===== Paginated Queries =====

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL")
    Page<Category> findRootCategories(Pageable pageable);

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.isActive = true")
    Page<Category> findActiveRootCategories(Pageable pageable);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId")
    Page<Category> findByParentId(@Param("parentId") UUID parentId, Pageable pageable);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.isActive = true")
    Page<Category> findActiveByParentId(@Param("parentId") UUID parentId, Pageable pageable);

    // ===== Slug Validation =====

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c WHERE c.slug = :slug AND c.id != :id")
    boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("id") UUID id);

    // ===== Enhanced Hierarchy Queries =====

    /**
     * FIXED: Find all descendant categories using JPQL instead of native query
     * This works properly with JPA entity mapping and prevents the 500 error
     */
    @Query(value = """
        WITH RECURSIVE descendants AS (
            SELECT id FROM categories WHERE parent_id = :categoryId
            UNION ALL
            SELECT c.id FROM categories c
            INNER JOIN descendants d ON c.parent_id = d.id
        )
        SELECT c.* FROM categories c 
        WHERE c.id IN (SELECT id FROM descendants)
        ORDER BY c.display_order ASC
        """, nativeQuery = true)
    List<Category> findAllDescendants(@Param("categoryId") UUID categoryId);

    /**
     * Alternative JPQL approach (safer, but may have performance impact on deep hierarchies)
     */
    @Query("SELECT c FROM Category c WHERE c.id IN " +
            "(SELECT child.id FROM Category child WHERE child.parent.id = :categoryId)")
    List<Category> findDirectDescendantsJpql(@Param("categoryId") UUID categoryId);

    /**
     * Get max depth of category hierarchy
     */
    @Query(value = """
        WITH RECURSIVE category_depth AS (
            SELECT id, parent_id, 1 as depth
            FROM categories WHERE parent_id IS NULL
            UNION ALL
            SELECT c.id, c.parent_id, cd.depth + 1
            FROM categories c
            INNER JOIN category_depth cd ON c.parent_id = cd.id
        )
        SELECT COALESCE(MAX(depth), 0) FROM category_depth
        """, nativeQuery = true)
    int findMaxCategoryDepth();

    /**
     * Check if a category is an ancestor of another
     */
    @Query(value = """
        WITH RECURSIVE ancestors AS (
            SELECT parent_id FROM categories WHERE id = :childId
            UNION ALL
            SELECT c.parent_id FROM categories c
            INNER JOIN ancestors a ON c.id = a.parent_id
            WHERE c.parent_id IS NOT NULL
        )
        SELECT CASE WHEN :ancestorId IN (SELECT parent_id FROM ancestors WHERE parent_id IS NOT NULL) 
               THEN true ELSE false END
        """, nativeQuery = true)
    boolean isAncestor(@Param("ancestorId") UUID ancestorId, @Param("childId") UUID childId);

    // ===== Product Count Queries (Enhanced) =====

    /**
     * Count all products (active and inactive) for a category and its descendants
     */
    @Query(value = """
        WITH RECURSIVE category_tree AS (
            SELECT id FROM categories WHERE id = :categoryId
            UNION ALL
            SELECT c.id FROM categories c
            INNER JOIN category_tree ct ON c.parent_id = ct.id
        )
        SELECT COUNT(p.id) 
        FROM products p
        INNER JOIN category_tree ct ON p.category_id = ct.id
        """, nativeQuery = true)
    Long countAllProductsByCategoryId(@Param("categoryId") UUID categoryId);

    /**
     * RECURSIVE product counts for multiple categories (BATCH OPERATION)
     * Prevents N+1 queries when displaying category lists
     * Each category gets count of its own products + all descendant products
     */
    @Query(value = """
        WITH RECURSIVE category_trees AS (
            -- Start with all requested categories
            SELECT id, id as root_id FROM categories WHERE id = ANY(CAST(:categoryIds AS uuid[]))
            UNION ALL
            -- Get all descendants
            SELECT c.id, ct.root_id 
            FROM categories c
            INNER JOIN category_trees ct ON c.parent_id = ct.id
        )
        SELECT ct.root_id as categoryId, COUNT(DISTINCT p.id) as count
        FROM category_trees ct
        LEFT JOIN products p ON p.category_id = ct.id AND p.is_active = true
        GROUP BY ct.root_id
        """, nativeQuery = true)
    List<Object[]> findProductCountsByCategoryIds(@Param("categoryIds") List<UUID> categoryIds);

    /**
     * Helper method to convert recursive product counts to map
     */
    default Map<UUID, Long> findProductCountsByIds(List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> results = findProductCountsByCategoryIds(categoryIds);
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    /**
     * Count only DIRECT products (non-recursive) for a single category
     * Use this when you only want products directly assigned to this category
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId AND p.isActive = true")
    Long countDirectProductsByCategoryId(@Param("categoryId") UUID categoryId);

    // ===== Statistics Queries =====

    long countByIsActive(boolean isActive);

    @Query("SELECT COUNT(c) FROM Category c WHERE c.parent IS NULL")
    long countRootCategories();

    @Query("SELECT COUNT(DISTINCT c.id) FROM Category c WHERE EXISTS " +
            "(SELECT 1 FROM Product p WHERE p.category.id = c.id)")
    long countCategoriesWithProducts();

    @Query("SELECT COUNT(c) FROM Category c WHERE c.parent.id = :parentId")
    long countDirectChildren(@Param("parentId") UUID parentId);

    // ===== Bulk Operations =====

    @Modifying
    @Query("UPDATE Category c SET c.isActive = :isActive WHERE c.id IN :ids")
    int bulkUpdateStatus(@Param("ids") List<UUID> ids, @Param("isActive") Boolean isActive);

    @Modifying
    @Query("DELETE FROM Category c WHERE c.id IN :ids")
    int bulkDeleteByIds(@Param("ids") List<UUID> ids);

    @Modifying
    @Query("UPDATE Category c SET c.displayOrder = :newOrder WHERE c.id = :categoryId")
    int updateDisplayOrder(@Param("categoryId") UUID categoryId, @Param("newOrder") Integer newOrder);

    // ===== Search & Filter =====

    @Query("SELECT c FROM Category c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Category> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT c FROM Category c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Category> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT c FROM Category c 
        WHERE (:isActive IS NULL OR c.isActive = :isActive)
        AND (:hasParent IS NULL OR (CASE WHEN :hasParent = true THEN c.parent IS NOT NULL 
                                          ELSE c.parent IS NULL END))
        ORDER BY c.displayOrder ASC, c.name ASC
        """)
    List<Category> findWithFilters(
            @Param("isActive") Boolean isActive,
            @Param("hasParent") Boolean hasParent
    );

    @Query("""
        SELECT c FROM Category c 
        WHERE (:isActive IS NULL OR c.isActive = :isActive)
        AND (:parentId IS NULL OR c.parent.id = :parentId)
        AND (:keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Category> findWithAdvancedFilters(
            @Param("isActive") Boolean isActive,
            @Param("parentId") UUID parentId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // ===== Category Path Queries =====

    /**
     * Get full path from root to category as concatenated string
     */
    @Query(value = """
        WITH RECURSIVE category_path AS (
            SELECT id, name, parent_id, CAST(name AS VARCHAR(1000)) as path, 1 as level
            FROM categories WHERE id = :categoryId
            UNION ALL
            SELECT c.id, c.name, c.parent_id, 
                   CAST(c.name || ' / ' || cp.path AS VARCHAR(1000)), cp.level + 1
            FROM categories c
            INNER JOIN category_path cp ON c.id = cp.parent_id
        )
        SELECT path FROM category_path ORDER BY level DESC LIMIT 1
        """, nativeQuery = true)
    String getCategoryFullPath(@Param("categoryId") UUID categoryId);

    // ===== Existence Checks =====

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Category c WHERE c.slug = :slug")
    boolean slugExists(@Param("slug") String slug);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Category c WHERE c.name = :name AND c.parent.id = :parentId")
    boolean existsByNameAndParentId(@Param("name") String name, @Param("parentId") UUID parentId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Category c WHERE c.name = :name AND c.parent IS NULL")
    boolean existsByNameAndParentIsNull(@Param("name") String name);

    /**
     * Check if a category with the same slug exists, excluding a specific category ID
     * Used during updates to allow keeping the same slug
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Category c WHERE LOWER(c.slug) = LOWER(:slug) AND (:excludeId IS NULL OR c.id != :excludeId)")
    boolean existsBySlugExcludingId(@Param("slug") String slug, @Param("excludeId") UUID excludeId);

    /**
     * Check if a root category with the same name exists (case-insensitive)
     * Used to prevent duplicate root categories
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Category c WHERE LOWER(c.name) = LOWER(:name) AND c.parent IS NULL AND (:excludeId IS NULL OR c.id != :excludeId)")
    boolean existsRootCategoryByNameExcludingId(@Param("name") String name, @Param("excludeId") UUID excludeId);

    /**
     * Check if a subcategory with the same name exists under the same parent (case-insensitive)
     * Used to prevent duplicate subcategories under the same parent
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Category c WHERE LOWER(c.name) = LOWER(:name) AND c.parent.id = :parentId AND (:excludeId IS NULL OR c.id != :excludeId)")
    boolean existsSubCategoryByNameAndParentExcludingId(@Param("name") String name, @Param("parentId") UUID parentId, @Param("excludeId") UUID excludeId);

    // ===== Special Queries for Admin Dashboard =====

    /**
     * Find categories without products (empty categories)
     */
    @Query("SELECT c FROM Category c WHERE NOT EXISTS " +
            "(SELECT 1 FROM Product p WHERE p.category.id = c.id)")
    List<Category> findCategoriesWithoutProducts();

    @Query("SELECT c FROM Category c WHERE NOT EXISTS " +
            "(SELECT 1 FROM Product p WHERE p.category.id = c.id)")
    Page<Category> findCategoriesWithoutProducts(Pageable pageable);

    /**
     * Find leaf categories (categories without children)
     */
    @Query("SELECT c FROM Category c WHERE NOT EXISTS " +
            "(SELECT 1 FROM Category child WHERE child.parent.id = c.id)")
    List<Category> findLeafCategories();

    /**
     * Get categories sorted by product count
     */
    @Query("""
        SELECT c, COUNT(p) as productCount 
        FROM Category c 
        LEFT JOIN Product p ON p.category.id = c.id 
        WHERE c.isActive = true 
        GROUP BY c 
        ORDER BY productCount DESC
        """)
    List<Object[]> findCategoriesSortedByProductCount();
}