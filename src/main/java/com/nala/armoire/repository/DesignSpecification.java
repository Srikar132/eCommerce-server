package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Design;
import org.springframework.data.jpa.domain.Specification;

public class DesignSpecification {

    /**
     * Filter only active designs
     */
    public static Specification<Design> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("isActive"));
    }

    /**
     * Filter designs by category slug
     */
    public static Specification<Design> hasCategorySlug(String categorySlug) {
        return (root, query, cb) -> {
            if (categorySlug == null || categorySlug.trim().isEmpty()) {
                return cb.conjunction();
            }
            return cb.equal(root.get("category").get("slug"), categorySlug);
        };
    }

    /**
     * Filter designs by category ID
     */
    public static Specification<Design> hasCategoryId(java.util.UUID categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("category").get("id"), categoryId);
        };
    }

    /**
     * Search designs by query in name, description, and tags
     * Uses full-text search across multiple fields
     */
    public static Specification<Design> searchByQuery(String searchQuery) {
        return (root, query, cb) -> {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                return cb.conjunction();
            }
            
            String pattern = "%" + searchQuery.toLowerCase().trim() + "%";
            
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("tags")), pattern)
            );
        };
    }

    /**
     * Filter designs by minimum price
     */
    public static Specification<Design> hasPriceGreaterThanOrEqual(java.math.BigDecimal minPrice) {
        return (root, query, cb) -> {
            if (minPrice == null) {
                return cb.conjunction();
            }
            return cb.greaterThanOrEqualTo(root.get("designPrice"), minPrice);
        };
    }

    /**
     * Filter designs by maximum price
     */
    public static Specification<Design> hasPriceLessThanOrEqual(java.math.BigDecimal maxPrice) {
        return (root, query, cb) -> {
            if (maxPrice == null) {
                return cb.conjunction();
            }
            return cb.lessThanOrEqualTo(root.get("designPrice"), maxPrice);
        };
    }

    /**
     * Search designs by name only (for autocomplete)
     */
    public static Specification<Design> searchByName(String nameQuery) {
        return (root, query, cb) -> {
            if (nameQuery == null || nameQuery.trim().isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + nameQuery.toLowerCase().trim() + "%";
            return cb.like(cb.lower(root.get("name")), pattern);
        };
    }
}
