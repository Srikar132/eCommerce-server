package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Category;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.model.entity.ProductVariant;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("isActive"));
    }

    public static Specification<Product> isNotDraft() {
        return (root, query, cb) -> cb.isFalse(root.get("isDraft"));
    }

    /**
     * Filter products by category slugs INCLUDING all child categories (OR logic)
     * Example: category=men -> returns products in "men" AND "men-tshirts", "men-bottomwear", etc.
     * 
     * This performs a hierarchical search:
     * 1. Find products directly in the specified category
     * 2. Find products in any child category (recursive)
     */
    public static Specification<Product> hasCategorySlugs(List<String> categorySlugs) {
        return (root, query, cb) -> {
            if (categorySlugs == null || categorySlugs.isEmpty()) {
                return cb.conjunction();
            }

            Join<Product, Category> categoryJoin = root.join("category", JoinType.INNER);
            
            // Create a list to hold all predicates
            List<Predicate> categoryPredicates = new ArrayList<>();
            
            for (String categorySlug : categorySlugs) {
                // Match direct category
                Predicate directMatch = cb.equal(categoryJoin.get("slug"), categorySlug);
                
                // Match child categories (where parent.slug = categorySlug)
                Predicate childMatch = cb.equal(
                    categoryJoin.get("parent").get("slug"), 
                    categorySlug
                );
                
                // Match grandchild categories (where parent.parent.slug = categorySlug)
                Predicate grandchildMatch = cb.equal(
                    categoryJoin.get("parent").get("parent").get("slug"), 
                    categorySlug
                );
                
                // Combine: direct OR child OR grandchild
                categoryPredicates.add(cb.or(directMatch, childMatch, grandchildMatch));
            }
            
            // Combine all category predicates with OR
            return cb.or(categoryPredicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter products by brand slugs (OR logic)
     * Example: brand=nike,adidas -> returns products from nike OR adidas
     */
    public static Specification<Product> hasBrandSlugs(List<String> brandSlugs) {
        return (root, query, cb) -> {
            if (brandSlugs == null || brandSlugs.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("brand").get("slug").in(brandSlugs);
        };
    }

    public static Specification<Product> hasPriceGreaterThanOrEqual(BigDecimal minPrice) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("basePrice"), minPrice);
    }

    public static Specification<Product> hasPriceLessThanOrEqual(BigDecimal maxPrice) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("basePrice"), maxPrice);
    }

    public static Specification<Product> isCustomizable(Boolean isCustomizable) {
        return (root, query, cb) ->
                cb.equal(root.get("isCustomizable"), isCustomizable);
    }

    public static Specification<Product> hasMaterial(String material) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("material")),
                        "%" + material.toLowerCase() + "%"
                );
    }

    /**
     * Filter products by variant sizes (OR logic)
     * Returns products that have at least one active variant with ANY of the specified sizes
     * Example: size=M,L -> returns products that have variants with size M OR L
     */
    public static Specification<Product> hasVariantWithSizes(List<String> sizes) {
        return (root, query, cb) -> {
            if (sizes == null || sizes.isEmpty()) {
                return cb.conjunction();
            }

            Join<Product, ProductVariant> variantJoin = root.join("variants", JoinType.INNER);
            if (query != null) {
                query.distinct(true);
            }

            Predicate variantActive = cb.isTrue(variantJoin.get("isActive"));
            Predicate sizeMatch = variantJoin.get("size").in(sizes);

            return cb.and(variantActive, sizeMatch);
        };
    }

    /**
     * Filter products by variant colors (OR logic, case-insensitive)
     * Returns products that have at least one active variant with ANY of the specified colors
     */
    public static Specification<Product> hasVariantWithColors(List<String> colors) {
        return (root, query, cb) -> {
            if (colors == null || colors.isEmpty()) {
                return cb.conjunction();
            }

            Join<Product, ProductVariant> variantJoin = root.join("variants", JoinType.INNER);
            if (query != null) {
                query.distinct(true);
            }

            Predicate variantActive = cb.isTrue(variantJoin.get("isActive"));

            Predicate colorMatch = cb.or(
                    colors.stream()
                            .map(color -> cb.equal(
                                    cb.lower(variantJoin.get("color")),
                                    color.toLowerCase()
                            ))
                            .toArray(Predicate[]::new)
            );

            return cb.and(variantActive, colorMatch);
        };
    }

    /**
     * Search products by keyword in name, description, or material
     */
    public static Specification<Product> searchByKeyword(String keyword) {
        return (root, query, cb) -> {
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("material")), pattern)
            );
        };
    }

    /**
     * Search products by query in name or description
     */
    public static Specification<Product> searchByNameOrDescription(String searchQuery) {
        return (root, query, cb) -> {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + searchQuery.toLowerCase().trim() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}