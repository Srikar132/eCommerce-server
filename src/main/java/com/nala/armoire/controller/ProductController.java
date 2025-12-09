package com.nala.armoire.controller;

import com.nala.armoire.annotation.CurrentUser;
import com.nala.armoire.model.dto.request.AddReviewRequest;
import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.model.dto.response.ProductVariantDTO;
import com.nala.armoire.model.dto.response.ReviewDTO;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
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
            @RequestParam(defaultValue = "createdAt:desc") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "24") int limit
    ) {
        PagedResponse<ProductDTO> products = productService.getProducts(
                category, brand, minPrice, maxPrice, size, color,
                customizable, sort, page, limit
        );

        return ResponseEntity.ok(products);

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
    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewDTO> addProductReview(
            @PathVariable UUID id,
            @Valid @RequestBody AddReviewRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        ReviewDTO review = productService.addProductReview(id, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }


//    GET /api/v1/products/{productId}/compatible-designs


}