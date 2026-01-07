package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
import com.nala.armoire.model.dto.request.AddReviewRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.DesignService;
import com.nala.armoire.service.ProductSearchService;
import com.nala.armoire.service.ProductService;
import com.nala.armoire.service.ProductSyncService;
import com.nala.armoire.util.PagedResponseUtil;
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
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final DesignService designService;
    private final ProductSearchService productSearchService;

    private final ProductSyncService productSyncService;

    @PostMapping("/sync-products")
    public ResponseEntity<String> syncAllProducts() {
        productSyncService.syncAllProducts();
        return ResponseEntity.ok("Synced all products to Elasticsearch");
    }

    /**
     * GET /api/v1/products - Search & Filter Products (Elasticsearch)
     *
     * Examples:
     * - /api/v1/products?category=men-tshirts
     * - /api/v1/products?category=men-tshirts&brand=nike
     * - /api/v1/products?searchQuery=cotton+shirt&minPrice=500
     * - /api/v1/products?category=men-topwear,men-bottomwear&productSize=M,L
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

        ProductSearchResponse response = productSearchService.getProducts(
                category, brand, minPrice, maxPrice, productSize, color,
                customizable, searchQuery, pageable
        );

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/products/autocomplete - Search Suggestions
     *
     * Example: /api/v1/products/autocomplete?query=cot
     * Returns: ["Cotton T-Shirt", "Cotton Shirt", "Cotton Joggers"]
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        log.info("GET /api/v1/products/autocomplete - query: {}", query);

        List<String> suggestions = productSearchService.getAutocomplete(query, limit);

        return ResponseEntity.ok(suggestions);
    }

    /**
     * GET /api/v1/products/{slug} - Get Single Product (PostgreSQL)
     *
     * Example: /api/v1/products/nike-cotton-tshirt-black
     */
    @GetMapping("/{slug}")
    public ResponseEntity<ProductDTO> getProductBySlug(@PathVariable String slug) {
        log.info("GET /api/v1/products/{}", slug);

        ProductDTO product = productService.getProductBySlug(slug);

        return ResponseEntity.ok(product);
    }

    /**
     * GET /api/v1/products/{id}/variants - Get Product Variants
     */
    @GetMapping("/{id}/variants")
    public ResponseEntity<List<ProductVariantDTO>> getProductVariants(@PathVariable UUID id) {
        log.info("GET /api/v1/products/{}/variants", id);

        List<ProductVariantDTO> variants = productService.getProductVariants(id);

        return ResponseEntity.ok(variants);
    }

    /**
     * GET /api/v1/products/{id}/reviews - Get Product Reviews
     */
    @GetMapping("/{id}/reviews")
    public ResponseEntity<PagedResponse<ReviewDTO>> getProductReviews(
            @PathVariable UUID id,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        log.info("GET /api/v1/products/{}/reviews", id);

        Page<ReviewDTO> reviews = productService.getProductReviews(id, pageable);

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

    //POST /api/v1/products/:id/reviews - Add review (Authenticated users only)
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewDTO> addProductReview(
            @PathVariable UUID id,
            @Valid @RequestBody AddReviewRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        ReviewDTO review = productService.addProductReview(id, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }


    //  GET /api/v1/products/{productId}/compatible-designs
    @GetMapping("/{id}/compatible-designs")
    public ResponseEntity<ApiResponse<PagedResponse<DesignListDTO>>> getCompatibleDesigns(
            @PathVariable Long id,
            @RequestParam String productType,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        log.info("GET /api/products/{}/compatible-designs?productType={}", id, productType);

        Page<DesignListDTO> pageResult =
                designService.getCompatibleDesigns(id, productType, page, size);

        PagedResponse<DesignListDTO> response =
                PagedResponseUtil.fromPage(pageResult);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Compatible designs retrieved")
        );
    }
}