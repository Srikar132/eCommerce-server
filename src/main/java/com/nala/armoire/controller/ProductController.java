package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
import com.nala.armoire.model.dto.request.AddReviewRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.DesignService;
import com.nala.armoire.service.ProductService;
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
    /*
     * GET /api/v1/products - List products with filters
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ProductDTO>> getProducts(
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> size,
            @RequestParam(required = false) List<String> color,
            @RequestParam(required = false) Boolean customizable,
            @PageableDefault(size = 24, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProductDTO> pageResult = productService.getProducts(
                category, brand, minPrice, maxPrice, size, color,
                customizable, pageable
        );

        PagedResponse<ProductDTO> response = PagedResponse.<ProductDTO>builder()
                .content(pageResult.getContent())
                .page(pageResult.getNumber() + 1) // Convert 0-based to 1-based
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .hasNext(pageResult.hasNext())
                .hasPrevious(pageResult.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    //GET /api/v1/products/:slug - Get product details by slug
    @GetMapping("/{slug}")
    public ResponseEntity<ProductDTO> getProductBySlug(@PathVariable String slug) {

        ProductDTO product = productService.getProductBySlug(slug);

        return ResponseEntity.ok(product);
    }

    // GET /api/v1/products/:id/variants - Get product variants
    @GetMapping("/{id}/variants")
    public ResponseEntity<List<ProductVariantDTO>> getProductVariants(@PathVariable UUID id) {
        List<ProductVariantDTO> variants = productService.getProductVariants(id);

        return ResponseEntity.ok(variants);
    }

    //GET /api/v1/products/:id/reviews - Get product reviews
    @GetMapping("/{id}/reviews")
    public ResponseEntity<Page<ReviewDTO>> getProductReviews(@PathVariable UUID id,
                                                             @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<ReviewDTO> reviews = productService.getProductReviews(id, pageable);

        return ResponseEntity.ok(reviews);
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