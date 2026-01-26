package com.nala.armoire.service;

import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.model.entity.*;
import com.nala.armoire.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    /**
     * Get best selling products (cached for 15 minutes)
     *
     * Algorithm:
     * 1. Count total quantity sold for each product
     * 2. Filter by category if provided
     * 3. Return top N products by sales count
     *
     * @param categorySlug Optional category filter (supports hierarchical - e.g., "men" includes "men-tshirts")
     * @param limit Number of products to return
     */
    @Cacheable(value = "bestSellers", key = "#categorySlug + '_' + #limit")
    @Transactional(readOnly = true)
    public List<ProductDTO> getBestSellingProducts(String categorySlug, Integer limit) {
        log.info("Fetching best sellers - category: {}, limit: {}", categorySlug, limit);

        List<UUID> bestSellerIds = orderItemRepository.findBestSellingProductIds(
                categorySlug,
                PageRequest.of(0, limit)
        );

        if (bestSellerIds.isEmpty()) {
            // Fallback to newest products if no sales data
            return getNewestProducts(categorySlug, limit);
        }

        // Fetch products and maintain order
        List<Product> products = productRepository.findAllById(bestSellerIds);

        // Map to DTOs while preserving the order from bestSellerIds
        Map<UUID, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return bestSellerIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .filter(p -> p.getIsActive() && !p.getIsDraft())
                .map(this::mapToProductDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get personalized product recommendations
     *
     * Multi-tier recommendation algorithm:
     *
     * For authenticated users:
     * 1. Get products from categories the user has purchased from
     * 2. Get products similar to what they've reviewed positively
     * 3. Fill remaining with best sellers
     *
     * For guest users:
     * - Return best sellers or trending products
     *
     * @param userId Optional user ID (null for guest users)
     * @param excludeProductId Optional product to exclude (useful on product detail pages)
     * @param categorySlug Optional category filter
     * @param limit Number of recommendations to return
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> getPersonalizedRecommendations(
            UUID userId,
            String excludeProductId,
            String categorySlug,
            Integer limit) {

        log.info("Generating recommendations - userId: {}, category: {}, limit: {}",
                userId, categorySlug, limit);

        List<ProductDTO> recommendations = new ArrayList<>();

        if (userId != null) {
            // Authenticated user - personalized recommendations
            recommendations = getPersonalizedForUser(userId, excludeProductId, categorySlug, limit);
        } else {
            // Guest user - show best sellers
            recommendations = getBestSellingProducts(categorySlug, limit);
        }

        // Filter out the excluded product if specified
        if (excludeProductId != null) {
            try {
                UUID excludeId = UUID.fromString(excludeProductId);
                recommendations = recommendations.stream()
                        .filter(p -> !p.getId().equals(excludeId))
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid excludeProductId format: {}", excludeProductId);
            }
        }

        return recommendations.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Generate personalized recommendations for authenticated users
     */
    private List<ProductDTO> getPersonalizedForUser(
            UUID userId,
            String excludeProductId,
            String categorySlug,
            Integer limit) {

        Set<ProductDTO> recommendations = new LinkedHashSet<>(); // Maintain order, avoid duplicates

        // Step 1: Get categories user has purchased from
        List<String> userPreferredCategories = orderItemRepository
                .findCategoriesByUserId(userId);

        if (!userPreferredCategories.isEmpty()) {
            // Get products from user's preferred categories
            List<ProductDTO> categoryBasedRecs = getProductsFromCategories(
                    userPreferredCategories,
                    excludeProductId,
                    limit
            );
            recommendations.addAll(categoryBasedRecs);
        }

        // Step 2: If category filter specified, add more from that category
        if (categorySlug != null && recommendations.size() < limit) {
            List<ProductDTO> categoryProducts = getBestSellingProducts(
                    categorySlug,
                    limit - recommendations.size()
            );
            recommendations.addAll(categoryProducts);
        }

        // Step 3: Fill remaining with overall best sellers
        if (recommendations.size() < limit) {
            List<ProductDTO> bestSellers = getBestSellingProducts(null, limit);
            recommendations.addAll(bestSellers);
        }

        return recommendations.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get products from specified categories
     */
    private List<ProductDTO> getProductsFromCategories(
            List<String> categorySlugs,
            String excludeProductId,
            Integer limit) {

        Specification<Product> spec = ProductSpecification.isActive()
                .and(ProductSpecification.isNotDraft())
                .and(ProductSpecification.hasCategorySlugs(categorySlugs));

        Pageable pageable = PageRequest.of(0, limit);
        List<Product> products = productRepository.findAll(spec, pageable).getContent();

        return products.stream()
                .map(this::mapToProductDTO)
                .collect(Collectors.toList());
    }

    /**
     * Fallback: Get newest products when no sales data available
     */
    private List<ProductDTO> getNewestProducts(String categorySlug, Integer limit) {
        Specification<Product> spec = ProductSpecification.isActive()
                .and(ProductSpecification.isNotDraft());

        if (categorySlug != null) {
            spec = spec.and(ProductSpecification.hasCategorySlugs(List.of(categorySlug)));
        }

        Pageable pageable = PageRequest.of(0, limit);
        List<Product> products = productRepository.findAll(spec, pageable).getContent();

        return products.stream()
                .map(this::mapToProductDTO)
                .collect(Collectors.toList());
    }

    // ==================== MAPPING METHOD ====================

    private ProductDTO mapToProductDTO(Product product) {
        Double averageRating = reviewRepository.findAverageRatingByProductId(product.getId());
        Long reviewCount = reviewRepository.countByProductId(product.getId());

        String primaryImageUrl = product.getVariants().stream()
                .filter(v -> v.getIsActive() && !v.getImages().isEmpty())
                .flatMap(v -> v.getImages().stream())
                .filter(img -> img.getIsPrimary())
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(
                        product.getVariants().stream()
                                .filter(v -> v.getIsActive() && !v.getImages().isEmpty())
                                .flatMap(v -> v.getImages().stream())
                                .findFirst()
                                .map(ProductImage::getImageUrl)
                                .orElse(null)
                );

        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .sku(product.getSku())
                .isCustomizable(product.getIsCustomizable())
                .material(product.getMaterial())
                .careInstructions(product.getCareInstructions())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .imageUrl(primaryImageUrl)
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .isActive(product.getIsActive())
                .isDraft(product.getIsDraft())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
