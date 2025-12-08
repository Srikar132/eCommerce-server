package com.nala.armoire.service;

import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.repository.ProductRepository;
import com.nala.armoire.repository.ProductSpecification;
import com.nala.armoire.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;

    public Page<ProductDTO> searchProducts(
            String query,
            String categorySlug,
            String brandSlug,
            Pageable pageable
    ) {
        Specification<Product> spec = (root, criteriaQuery, cb) -> cb.conjunction();

// Add active filter
        spec = spec.and(ProductSpecification.isActive());

        // Add search keyword specification
        if (query != null && !query.trim().isEmpty()) {
            spec = spec.and(ProductSpecification.searchByKeyword(query.trim()));
        }

        // Add category filter if provided
        if (categorySlug != null && !categorySlug.trim().isEmpty()) {
            spec = spec.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            root.get("category").get("slug"),
                            categorySlug.trim()
                    )
            );
        }

        if (brandSlug != null && !brandSlug.trim().isEmpty()) {
            spec = spec.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(
                            root.get("brand").get("slug"),
                            brandSlug.trim()
                    )
            );
        }

        Page<Product> products = productRepository.findAll(spec, pageable);

        return products.map(product -> {
            Double averageRating = reviewRepository.findAverageRatingByProductId(product.getId());
            Long reviewCount = reviewRepository.countByProductId(product.getId());

            return ProductDTO.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .slug(product.getSlug())
                    .description(product.getDescription())
                    .basePrice(product.getBasePrice())
                    .sku(product.getSku())
                    .isCustomizable(product.getIsCustomizable())
                    .material(product.getMaterial())
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                    .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                    .averageRating(averageRating)
                    .reviewCount(reviewCount)
                    .isActive(product.getIsActive())
                    .createdAt(product.getCreatedAt())
                    .build();
        });
    }
}
