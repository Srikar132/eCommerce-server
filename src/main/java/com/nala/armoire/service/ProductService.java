package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.request.AddReviewRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.model.entity.*;
import com.nala.armoire.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ProductImageRepository productImageRepository;

    //get all products
    @Transactional(readOnly = true)
    public PagedResponse<ProductDTO> getProducts(
            List<String> categorySlugs,
            List<String> brandSlugs,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> sizes,
            List<String> colors,
            Boolean isCustomizable,
            String sort,
            int page,
            int limit
    ) {

        Specification<Product> spec = (root, query, cb) -> cb.conjunction();

//        spec = spec.and(ProductSpecification.isActive());


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

        Sort sorting = parseSortParameter(sort);

        Pageable pageable = PageRequest.of(page - 1, limit, sorting);

        Page<Product> productPage = productRepository.findAll(spec, pageable);

//        System.out.println(productPage);

        List<ProductDTO> productDTOs = productPage.getContent()
                .stream()
                .map(this::mapToProductDTO)
                .collect(Collectors.toList());

        System.out.println(productDTOs.size());


        return PagedResponse.<ProductDTO>builder()
                .content(productDTOs)
                .page(page)
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .first(productPage.isFirst())
                .last(productPage.isLast())
                .hasNext(productPage.hasNext())
                .hasPrevious(productPage.hasPrevious())
                .build();
    }

    private Sort parseSortParameter(String sortParam) {
        if (sortParam == null || sortParam.trim().isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        List<Sort.Order> orders = new ArrayList<>();
        String[] sortFields = sortParam.split(",");

        for (String field : sortFields) {
            String[] parts = field.trim().split(":");
            String property = parts[0].trim();
            Sort.Direction direction = Sort.Direction.DESC;

            if (parts.length > 1) {
                direction = "asc".equalsIgnoreCase(parts[1].trim())
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;
            }

            // Validate sort fields
            if (isValidSortField(property)) {
                orders.add(new Sort.Order(direction, property));
            } else {
                log.warn("Invalid sort field: {}. Using default sort.", property);
            }
        }

        return orders.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(orders);
    }

    private boolean isValidSortField(String field) {
        // Define allowed sort fields
        List<String> validFields = List.of(
                "createdAt", "updatedAt", "name", "basePrice", "averageRating"
        );
        return validFields.contains(field);
    }

    //get product through slug

    @Transactional(readOnly = true)
    public ProductDTO getProductBySlug(String slug) {
        Product product = productRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not found"));

        return mapToProductDTO(product);
    }

    //get product through variants(size, color)
    @Transactional(readOnly = true)
    public List<ProductVariantDTO> getProductVariants(UUID productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not found"));

        List<ProductVariant> variants = productVariantRepository.findByProductIdAndIsActiveTrue(productId);

        return variants.stream()
                .map(this::mapToProductVariantDTO)
                .collect(Collectors.toList());
    }

    //get product reviews
    public Page<ReviewDTO> getProductReviews(UUID productId, Pageable pageable) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Page<Review> reviews = reviewRepository.findByProductId(productId, pageable);

        return reviews.map(this::mapToReviewDTO);
    }

    @Transactional
    public ReviewDTO addProductReview(UUID productId, UUID userId, AddReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if user already reviewed this product
        if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new BadRequestException("You have already reviewed this product");
        }

        // Check if user has purchased this product (for verified purchase)
        boolean hasPurchased = orderItemRepository.existsByUserIdAndProductId(userId, productId);

        Review review = Review.builder()
                .user(user)
                .product(product)
                .rating(request.getRating())
                .title(request.getTitle())
                .comment(request.getComment())
                .isVerifiedPurchase(hasPurchased)
                .build();

        Review savedReview = reviewRepository.save(review);
        log.info("Review added for productId: {} by userId: {}", productId, userId);

        return mapToReviewDTO(savedReview);
    }


    //Mapping methods
    private ProductDTO mapToProductDTO(Product product) {
        List<ProductImageDTO> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(product.getId())
                .stream()
                .map(this::mapToProductImageDTO)
                .collect(Collectors.toList());

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
                .careInstructions(product.getCareInstructions())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .images(images)
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .isActive(product.getIsActive())
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
                .build();
    }

    private ProductVariantDTO mapToProductVariantDTO(ProductVariant variant) {
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
