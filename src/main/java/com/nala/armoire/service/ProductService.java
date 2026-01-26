package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.request.AddReviewRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.model.entity.*;
import com.nala.armoire.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    

    // @Transactional(readOnly = true)
    // public PagedResponse<ProductDTO> getProducts(
    //         List<String> categorySlugs,
    //         List<String> brandSlugs,
    //         BigDecimal minPrice,
    //         BigDecimal maxPrice,
    //         List<String> sizes,
    //         List<String> colors,
    //         Boolean isCustomizable,
    //         String sort,
    //         int page,
    //         int limit) {

    //     Specification<Product> spec = ProductSpecification.isActive()
    //             .and(ProductSpecification.isNotDraft());

    //     if (categorySlugs != null && !categorySlugs.isEmpty()) {
    //         spec = spec.and(ProductSpecification.hasCategorySlugs(categorySlugs));
    //     }

    //     if (brandSlugs != null && !brandSlugs.isEmpty()) {
    //         spec = spec.and(ProductSpecification.hasBrandSlugs(brandSlugs));
    //     }

    //     if (minPrice != null) {
    //         spec = spec.and(ProductSpecification.hasPriceGreaterThanOrEqual(minPrice));
    //     }

    //     if (maxPrice != null) {
    //         spec = spec.and(ProductSpecification.hasPriceLessThanOrEqual(maxPrice));
    //     }

    //     if (isCustomizable != null) {
    //         spec = spec.and(ProductSpecification.isCustomizable(isCustomizable));
    //     }

    //     if (sizes != null && !sizes.isEmpty()) {
    //         spec = spec.and(ProductSpecification.hasVariantWithSizes(sizes));
    //     }

    //     if (colors != null && !colors.isEmpty()) {
    //         spec = spec.and(ProductSpecification.hasVariantWithColors(colors));
    //     }

    //     Sort sorting = parseSortParameter(sort);
    //     Pageable pageable = PageRequest.of(page - 1, limit, sorting);
    //     Page<Product> productPage = productRepository.findAll(spec, pageable);

    //     List<ProductDTO> productDTOs = productPage.getContent()
    //             .stream()
    //             .map(this::mapToProductDTO)
    //             .collect(Collectors.toList());

    //     System.out.println(productDTOs.size());

    //     return PagedResponse.<ProductDTO>builder()
    //             .content(productDTOs)
    //             .page(page)
    //             .size(productPage.getSize())
    //             .totalElements(productPage.getTotalElements())
    //             .totalPages(productPage.getTotalPages())
    //             .first(productPage.isFirst())
    //             .last(productPage.isLast())
    //             .hasNext(productPage.hasNext())
    //             .hasPrevious(productPage.hasPrevious())
    //             .build();
    // }

    // private Sort parseSortParameter(String sortParam) {
    //     if (sortParam == null || sortParam.trim().isEmpty()) {
    //         return Sort.by(Sort.Direction.DESC, "createdAt");
    //     }

    //     List<Sort.Order> orders = new ArrayList<>();
    //     String[] sortFields = sortParam.split(",");

    //     for (String field : sortFields) {
    //         String[] parts = field.trim().split(":");
    //         String property = parts[0].trim();
    //         Sort.Direction direction = Sort.Direction.DESC;

    //         if (parts.length > 1) {
    //             direction = "asc".equalsIgnoreCase(parts[1].trim())
    //                     ? Sort.Direction.ASC
    //                     : Sort.Direction.DESC;
    //         }

    //         if (isValidSortField(property)) {
    //             orders.add(new Sort.Order(direction, property));
    //         } else {
    //             log.warn("Invalid sort field: {}. Using default sort.", property);
    //         }
    //     }

    //     return orders.isEmpty()
    //             ? Sort.by(Sort.Direction.DESC, "createdAt")
    //             : Sort.by(orders);
    // }

    // private boolean isValidSortField(String field) {
    //     List<String> validFields = List.of(
    //             "createdAt", "updatedAt", "name", "basePrice", "averageRating");
    //     return validFields.contains(field);
    // }

    /**
     * Get product by slug with Redis caching
     * Cache TTL: 10 minutes (configured in RedisConfig)
     */
    @Cacheable(value = "products", key = "#slug")
    @Transactional(readOnly = true)
    public ProductDTO getProductBySlug(String slug) {
        log.info("Fetching product from database for slug: {}", slug);
        Product product = productRepository.findBySlugAndIsActiveTrueAndIsDraftFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not found"));

        return mapToProductDTO(product);
    }

    /**
     * Get product variants with Redis caching
     * Cache TTL: 10 minutes (configured in RedisConfig)
     */
    @Cacheable(value = "productVariants", key = "#slug")
    @Transactional(readOnly = true)
    public List<ProductVariantDTO> getProductVariants(String slug) {
        log.info("Fetching product variants from database for slug: {}", slug);
        Product product = productRepository.findBySlugAndIsActiveTrueAndIsDraftFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not found"));

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsActiveTrue(product.getId());

        return variants.stream()
                .map(this::mapToProductVariantDTO)
                .collect(Collectors.toList());
    }

    public Page<ReviewDTO> getProductReviews(String slug, Pageable pageable) {
        Product product = productRepository.findBySlugAndIsActiveTrueAndIsDraftFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Page<Review> reviews = reviewRepository.findByProductId(product.getId(), pageable);

        return reviews.map(this::mapToReviewDTO);
    }

    /**
     * Add product review and evict product cache
     * This evicts the cached product since review affects average rating
     */
    @CacheEvict(value = "products", key = "#slug")
    @Transactional
    public ReviewDTO addProductReview(String slug, UUID userId, AddReviewRequest request) {
        log.info("Adding review for product slug: {} - will evict cache", slug);
        Product product = productRepository.findBySlugAndIsActiveTrueAndIsDraftFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (reviewRepository.existsByUserIdAndProductId(userId, product.getId())) {
            throw new BadRequestException("You have already reviewed this product");
        }

        boolean hasPurchased = orderItemRepository.existsByUserIdAndProductId(userId, product.getId());

        Review review = Review.builder()
                .user(user)
                .product(product)
                .rating(request.getRating())
                .title(request.getTitle())
                .comment(request.getComment())
                .isVerifiedPurchase(hasPurchased)
                .build();

        Review savedReview = reviewRepository.save(review);
        log.info("Review added for product slug: {} by userId: {}", slug, userId);

        return mapToReviewDTO(savedReview);
    }



    /**
     * Search products with facets for filtering
     * Cache TTL: 5 minutes (configured in RedisConfig)
     * Cache key includes all filter parameters to ensure correct results
     */
    @Cacheable(
        value = "productSearch",
        key = "T(java.util.Objects).hash(#categorySlugs, #brandSlugs, #minPrice, #maxPrice, #sizes, #colors, #isCustomizable, #searchQuery, #pageable.pageNumber, #pageable.pageSize, #pageable.sort)"
    )
    @Transactional(readOnly = true)
    public ProductSearchResponse searchProductsWithFacets(
            List<String> categorySlugs,
            List<String> brandSlugs,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> sizes,
            List<String> colors,
            Boolean isCustomizable,
            String searchQuery,
            Pageable pageable) {

        log.info("Fetching products from database - category: {}, brand: {}, query: {}", 
                categorySlugs, brandSlugs, searchQuery);

        // Build specification for filtering
        Specification<Product> spec = ProductSpecification.isActive()
                .and(ProductSpecification.isNotDraft());

        if (categorySlugs != null && !categorySlugs.isEmpty()) {
            spec = spec.and(ProductSpecification.hasCategorySlugs(categorySlugs));
        }

        if (brandSlugs != null && !brandSlugs.isEmpty()) {
            spec = spec.and(ProductSpecification.hasBrandSlugs(brandSlugs));
        }

        if (minPrice != null) {
            spec = spec.and(ProductSpecification.hasPriceGreaterThanOrEqual(minPrice));
        }

        if (maxPrice != null) {
            spec = spec.and(ProductSpecification.hasPriceLessThanOrEqual(maxPrice));
        }

        if (isCustomizable != null) {
            spec = spec.and(ProductSpecification.isCustomizable(isCustomizable));
        }

        if (sizes != null && !sizes.isEmpty()) {
            spec = spec.and(ProductSpecification.hasVariantWithSizes(sizes));
        }

        if (colors != null && !colors.isEmpty()) {
            spec = spec.and(ProductSpecification.hasVariantWithColors(colors));
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            spec = spec.and(ProductSpecification.searchByNameOrDescription(searchQuery));
        }

        // Get paginated products
        Page<Product> productPage = productRepository.findAll(spec, pageable);
        List<ProductDTO> productDTOs = productPage.getContent()
                .stream()
                .map(this::mapToProductDTO)
                .collect(Collectors.toList());

        PagedResponse<ProductDTO> pagedProducts = PagedResponse.<ProductDTO>builder()
                .content(productDTOs)
                .page(productPage.getNumber())
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .first(productPage.isFirst())
                .last(productPage.isLast())
                .hasNext(productPage.hasNext())
                .hasPrevious(productPage.hasPrevious())
                .build();

        // Get facets
        ProductFacetsDTO facets = productRepository.getProductFacets(
                categorySlugs, brandSlugs, minPrice, maxPrice, sizes, colors, 
                isCustomizable, searchQuery);

        // Mark selected facets
        markSelectedFacets(facets, categorySlugs, brandSlugs, sizes, colors);

        return ProductSearchResponse.builder()
                .products(pagedProducts)
                .facets(facets)
                .build();
    }

    /**
     * Mark which facets are currently selected
     */
    private void markSelectedFacets(ProductFacetsDTO facets, 
                                     List<String> selectedCategories,
                                     List<String> selectedBrands,
                                     List<String> selectedSizes,
                                     List<String> selectedColors) {
        if (facets.getCategories() != null && selectedCategories != null) {
            facets.getCategories().forEach(cat -> 
                cat.setSelected(selectedCategories.contains(cat.getValue())));
        }

        if (facets.getBrands() != null && selectedBrands != null) {
            facets.getBrands().forEach(brand -> 
                brand.setSelected(selectedBrands.contains(brand.getValue())));
        }

        if (facets.getSizes() != null && selectedSizes != null) {
            facets.getSizes().forEach(size -> 
                size.setSelected(selectedSizes.contains(size.getValue())));
        }

        if (facets.getColors() != null && selectedColors != null) {
            facets.getColors().forEach(color -> 
                color.setSelected(selectedColors.contains(color.getValue())));
        }
    }

    /**
     * Get product name autocomplete suggestions with Redis caching
     * Cache TTL: 10 minutes (configured in RedisConfig)
     */
    @Cacheable(value = "autocomplete", key = "#query + '_' + #limit")
    @Transactional(readOnly = true)
    public List<String> getProductAutocomplete(String query, Integer limit) {
        log.info("Fetching autocomplete from database for query: {}", query);

        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        return productRepository.findProductNameAutocomplete(query.trim(), limit);
    }


        // ==================== MAPPING METHODS ====================

    // CHANGED: Extract primary image from first active variant
    private ProductDTO mapToProductDTO(Product product) {

        Double averageRating = reviewRepository.findAverageRatingByProductId(product.getId());
        Long reviewCount = reviewRepository.countByProductId(product.getId());

        // Get primary image from first active variant
        String primaryImageUrl = product.getVariants().stream()
                .filter(v -> v.getIsActive() && !v.getImages().isEmpty())
                .flatMap(v -> v.getImages().stream())
                .filter(img -> img.getIsPrimary())
                .findFirst()
                .map(img -> img.getImageUrl())
                .orElse(
                        // Fallback to first image from first active variant
                        product.getVariants().stream()
                                .filter(v -> v.getIsActive() && !v.getImages().isEmpty())
                                .flatMap(v -> v.getImages().stream())
                                .findFirst()
                                .map(img -> img.getImageUrl())
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

    private ProductImageDTO mapToProductImageDTO(ProductImage image) {
        return ProductImageDTO.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .altText(image.getAltText())
                .displayOrder(image.getDisplayOrder())
                .isPrimary(image.getIsPrimary())
                .imageRole(image.getImageRole())
                .build();
    }

    // CHANGED: Now includes variant images
    private ProductVariantDTO mapToProductVariantDTO(ProductVariant variant) {
        // Map variant images
        List<ProductImageDTO> variantImages = variant.getImages().stream()
                .map(this::mapToProductImageDTO)
                .collect(Collectors.toList());

        return ProductVariantDTO.builder()
                .id(variant.getId())
                .productId(variant.getProduct().getId())
                .size(variant.getSize())
                .color(variant.getColor())
                .colorHex(variant.getColorHex())
                .stockQuantity(variant.getStockQuantity())
                .additionalPrice(variant.getAdditionalPrice())
                .sku(variant.getSku())
                .isActive(variant.getIsActive())
                .images(variantImages) // NEW: Include images in variant DTO
                .build();
    }

    private ReviewDTO mapToReviewDTO(Review review) {
        return ReviewDTO.builder()
                .id(review.getId())
                .userId(review.getUser().getId())
                .userName(review.getUser().getUserName())
                .productId(review.getProduct().getId())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .isVerifiedPurchase(review.getIsVerifiedPurchase())
                .createdAt(review.getCreatedAt())
                .build();
    }
}