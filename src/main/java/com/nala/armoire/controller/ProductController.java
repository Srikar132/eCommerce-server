package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
import com.nala.armoire.model.dto.request.AddReviewRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.ProductService;
import com.nala.armoire.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final RecommendationService recommendationService;

    /**
     * GET /api/v1/products - Search & Filter Products
     */
    @GetMapping
    public ResponseEntity<ProductSearchResponse> searchProducts(
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> productSize,
            @RequestParam(required = false) List<String> color,
            @RequestParam(required = false) Boolean customizable,
            @RequestParam(required = false) String searchQuery,
            @PageableDefault(size = 24, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        log.info("GET /api/v1/products - category: {}, searchQuery: {}", category, searchQuery);

        ProductSearchResponse response = productService.searchProductsWithFacets(
                category, brand, minPrice, maxPrice, productSize, color,
                customizable, searchQuery, pageable
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/products/best-sellers - Get Best Selling Products
     *
     * Returns top selling products based on order history
     * Can be used on homepage, category pages, or anywhere you want to showcase popular items
     *
     * Example: /api/v1/products/best-sellers?limit=8
     * Example: /api/v1/products/best-sellers?category=men&limit=6
     */
    @GetMapping("/best-sellers")
    public ResponseEntity<List<ProductDTO>> getBestSellingProducts(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        log.info("GET /api/v1/products/best-sellers - category: {}, limit: {}", category, limit);

        List<ProductDTO> bestSellers = recommendationService.getBestSellingProducts(category, limit);

        return ResponseEntity.ok(bestSellers);
    }

    /**
     * GET /api/v1/products/recommendations - Get Personalized Product 
     * Returns personalized recommendations based on:
     * - User's purchase history
     * - User's browsing behavior
     * - User's preferences (if available)
     * - Popular items (fallback for new users)
     *
     * Can be used anywhere in the app: homepage, product pages, cart, checkout, etc.
     *
     * Examples:
     * - /api/v1/products/recommendations?limit=8 (authenticated user)
     * - /api/v1/products/recommendations?excludeProductId=uuid&limit=6 (on product detail page)
     * - /api/v1/products/recommendations?category=men&limit=4 (category-specific)
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<ProductDTO>> getRecommendations(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam(required = false) String excludeProductId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        log.info("GET /api/v1/products/recommendations - userId: {}, category: {}, limit: {}",
                currentUser != null ? currentUser.getId() : "guest", category, limit);

        List<ProductDTO> recommendations = recommendationService.getPersonalizedRecommendations(
                currentUser != null ? currentUser.getId() : null,
                excludeProductId,
                category,
                limit
        );

        return ResponseEntity.ok(recommendations);
    }

    /**
     * GET /api/v1/products/autocomplete - Search Suggestions
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        log.info("GET /api/v1/products/autocomplete - query: {}", query);

        List<String> suggestions = productService.getProductAutocomplete(query, limit);

        return ResponseEntity.ok(suggestions);
    }

    /**
     * GET /api/v1/products/{slug} - Get Single Product
     */
    @GetMapping("/{slug}")
    public ResponseEntity<ProductDTO> getProductBySlug(@PathVariable String slug) {
        log.info("GET /api/v1/products/{}", slug);

        ProductDTO product = productService.getProductBySlug(slug);

        return ResponseEntity.ok(product);
    }

    /**
     * GET /api/v1/products/{slug}/variants - Get Product Variants
     */
    @GetMapping("/{slug}/variants")
    public ResponseEntity<List<ProductVariantDTO>> getProductVariants(@PathVariable String slug) {
        log.info("GET /api/v1/products/{}/variants", slug);

        List<ProductVariantDTO> variants = productService.getProductVariants(slug);

        return ResponseEntity.ok(variants);
    }

    /**
     * GET /api/v1/products/{slug}/reviews - Get Product Reviews
     */
    @GetMapping("/{slug}/reviews")
    public ResponseEntity<PagedResponse<ReviewDTO>> getProductReviews(
            @PathVariable String slug,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        log.info("GET /api/v1/products/{}/reviews", slug);

        Page<ReviewDTO> reviews = productService.getProductReviews(slug, pageable);

        PagedResponse<ReviewDTO> response = PagedResponse.<ReviewDTO>builder()
                .content(reviews.getContent())
                .page(reviews.getNumber())
                .size(reviews.getSize())
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .first(reviews.isFirst())
                .last(reviews.isLast())
                .hasNext(reviews.hasNext())
                .hasPrevious(reviews.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/products/{slug}/reviews - Add review
     */
    @PostMapping("/{slug}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewDTO> addProductReview(
            @PathVariable String slug,
            @Valid @RequestBody AddReviewRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        ReviewDTO review = productService.addProductReview(slug, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }
}