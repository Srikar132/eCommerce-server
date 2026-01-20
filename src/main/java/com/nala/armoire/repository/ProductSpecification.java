package com.nala.armoire.repository;

import com.nala.armoire.model.entity.Product;
import com.nala.armoire.model.entity.ProductVariant;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("isActive"));
    }

    public static Specification<Product> isNotDraft() {
        return (root, query, cb) -> cb.isFalse(root.get("isDraft"));
    }

    /**
     * Filter products by category slugs (OR logic)
     * Example: category=t-shirts,jeans -> returns products in t-shirts OR jeans
     */
    public static Specification<Product> hasCategorySlugs(List<String> categorySlugs) {
        return (root, query, cb) -> {
            if (categorySlugs == null || categorySlugs.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("category").get("slug").in(categorySlugs);
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
     *
     * This uses INNER JOIN to product_variants table and checks:
     * 1. Variant is active
     * 2. Variant size matches any of the provided sizes
     * 3. Uses DISTINCT to avoid duplicate products
     */
    public static Specification<Product> hasVariantWithSizes(List<String> sizes) {
        return (root, query, cb) -> {
            if (sizes == null || sizes.isEmpty()) {
                return cb.conjunction();
            }

            // Join with variants table
            Join<Product, ProductVariant> variantJoin = root.join("variants", JoinType.INNER);

            // Add distinct to avoid duplicate products when multiple variants match
            query.distinct(true);

            // Check if variant is active
            Predicate variantActive = cb.isTrue(variantJoin.get("isActive"));

            // Check if size matches any of the provided sizes
            Predicate sizeMatch = variantJoin.get("size").in(sizes);

            return cb.and(variantActive, sizeMatch);
        };
    }

    /**
     * Filter products by variant colors (OR logic, case-insensitive)
     * Returns products that have at least one active variant with ANY of the specified colors
     * Example: color=black,blue -> returns products that have variants with color black OR blue
     *
     * This uses INNER JOIN to product_variants table and checks:
     * 1. Variant is active
     * 2. Variant color matches any of the provided colors (case-insensitive)
     * 3. Uses DISTINCT to avoid duplicate products
     */
    public static Specification<Product> hasVariantWithColors(List<String> colors) {
        return (root, query, cb) -> {
            if (colors == null || colors.isEmpty()) {
                return cb.conjunction();
            }

            // Join with variants table
            Join<Product, ProductVariant> variantJoin = root.join("variants", JoinType.INNER);

            // Add distinct to avoid duplicate products when multiple variants match
            query.distinct(true);

            // Check if variant is active
            Predicate variantActive = cb.isTrue(variantJoin.get("isActive"));

            // Check if color matches any of the provided colors (case-insensitive)
            // Build OR predicates for each color
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
}