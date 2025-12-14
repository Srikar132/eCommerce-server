package com.nala.armoire.repository;


import com.nala.armoire.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlugAndIsActiveTrue(String slug);

    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<Category> findAllByOrderByDisplayOrderAsc();

    boolean existsBySlug(String slug);

    // Find root categories (no parent)
    List<Category> findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();

    List<Category> findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID parentId);
    List<Category> findByParentIsNullOrderByDisplayOrderAsc();

    List<Category> findByParentIdOrderByDisplayOrderAsc(UUID parentId);

    boolean existsByParentId(UUID parentId);

    // Get category hierarchy with product counts
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.isActive = true ORDER BY c.displayOrder ASC")
    List<Category> findAllActiveWithParent();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent ORDER BY c.displayOrder ASC")
    List<Category> findAllWithParent();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId AND p.isActive = true")
    Long countProductsByCategoryId(@Param("categoryId") UUID categoryId);

    // Find all descendants of a category (for hierarchical operations)
    @Query(value = "WITH RECURSIVE category_tree AS (" +
            "SELECT id, parent_id FROM categories WHERE id = :categoryId " +
            "UNION ALL " +
            "SELECT c.id, c.parent_id FROM categories c " +
            "INNER JOIN category_tree ct ON c.parent_id = ct.id) " +
            "SELECT id FROM category_tree WHERE id != :categoryId",
            nativeQuery = true)
    List<UUID> findAllDescendantIds(@Param("categoryId") UUID categoryId);
}
