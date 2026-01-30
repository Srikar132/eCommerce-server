package com.nala.armoire.specification;

import com.nala.armoire.model.dto.request.ProductFilterRequest;
import com.nala.armoire.model.entity.Product;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification builder for Product filtering
 * Supports dynamic queries based on ProductFilterRequest
 */
@Component
@RequiredArgsConstructor
public class AdminProductSpecification {

    /**
     * Build specification from filter request
     */
    public Specification<Product> buildSpecification(ProductFilterRequest filter) {
        return (root, query, criteriaBuilder) -> {
            
            List<Predicate> predicates = new ArrayList<>();

            // Text search (name, description, SKU)
            if (filter.getSearch() != null && !filter.getSearch().trim().isEmpty()) {
                String searchPattern = "%" + filter.getSearch().toLowerCase() + "%";
                Predicate namePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("name")), searchPattern);
                Predicate descPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("description")), searchPattern);
                Predicate skuPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("sku")), searchPattern);
                predicates.add(criteriaBuilder.or(namePredicate, descPredicate, skuPredicate));
            }

            // Category filter
            if (filter.getCategoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), filter.getCategoryId()));
            }

            // Brand filter
            if (filter.getBrandId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("brand").get("id"), filter.getBrandId()));
            }

            // Status filters
            if (filter.getIsActive() != null) {
                predicates.add(criteriaBuilder.equal(root.get("isActive"), filter.getIsActive()));
            }

            if (filter.getIsDraft() != null) {
                predicates.add(criteriaBuilder.equal(root.get("isDraft"), filter.getIsDraft()));
            }

            if (filter.getIsCustomizable() != null) {
                predicates.add(criteriaBuilder.equal(root.get("isCustomizable"), filter.getIsCustomizable()));
            }

            // Price range filters
            if (filter.getMinPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("basePrice"), filter.getMinPrice()));
            }

            if (filter.getMaxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("basePrice"), filter.getMaxPrice()));
            }


            // Rating filter (if minRating is provided)
            if (filter.getMinRating() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("averageRating"), filter.getMinRating()));
            }

            // Remove duplicates for queries with joins
            if(query != null) {
                query.distinct(true);
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
