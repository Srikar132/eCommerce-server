package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.service.AdminProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin Product Management Controller
 * Requires ADMIN role
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final AdminProductService adminProductService;

    /**
     * GET /api/v1/admin/products
     * Get all products (includes inactive)
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ProductDTO>> getAllProducts(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.info("Admin: Fetching all products - page: {}, size: {}", page, size);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<ProductDTO> products = adminProductService.getAllProducts(pageable);

        PagedResponse<ProductDTO> response = PagedResponse.<ProductDTO>builder()
                .content(products.getContent())
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .first(products.isFirst())
                .last(products.isLast())
                .hasNext(products.hasNext())
                .hasPrevious(products.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/products
     * Create new product (can be draft or published)
     */
    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(@RequestBody Product product) {
        log.info("Admin: Creating new product: {} (draft: {})", 
                product.getName(), product.getIsDraft());

        ProductDTO createdProduct = adminProductService.createProduct(product);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    /**
     * PUT /api/v1/admin/products/{id}
     * Update existing product (supports partial updates for draft workflow with debounce)
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(
            @PathVariable UUID id,
            @RequestBody Product product) {

        log.info("Admin: Updating product: {} (draft: {})", id, product.getIsDraft());

        ProductDTO updatedProduct = adminProductService.updateProduct(id, product);

        return ResponseEntity.ok(updatedProduct);
    }

    /**
     * PATCH /api/v1/admin/products/{id}
     * Partial update for product (same as PUT, supports debounce saves)
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ProductDTO> patchProduct(
            @PathVariable UUID id,
            @RequestBody Product product) {

        log.info("Admin: Patching product: {}", id);

        ProductDTO updatedProduct = adminProductService.updateProduct(id, product);

        return ResponseEntity.ok(updatedProduct);
    }

    /**
     * PUT /api/v1/admin/products/{id}/status
     * Toggle product active/inactive
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<ProductDTO> toggleProductStatus(@PathVariable UUID id) {
        log.info("Admin: Toggling product status: {}", id);

        ProductDTO product = adminProductService.toggleProductStatus(id);

        return ResponseEntity.ok(product);
    }

    /**
     * PUT /api/v1/admin/products/{id}/publish
     * Publish a draft product
     */
    @PutMapping("/{id}/publish")
    public ResponseEntity<ProductDTO> publishProduct(@PathVariable UUID id) {
        log.info("Admin: Publishing product: {}", id);

        ProductDTO product = adminProductService.publishProduct(id);

        return ResponseEntity.ok(product);
    }

    /**
     * PUT /api/v1/admin/products/{id}/unpublish
     * Convert a published product back to draft
     */
    @PutMapping("/{id}/unpublish")
    public ResponseEntity<ProductDTO> unpublishProduct(@PathVariable UUID id) {
        log.info("Admin: Unpublishing product: {}", id);

        ProductDTO product = adminProductService.unpublishProduct(id);

        return ResponseEntity.ok(product);
    }

    /**
     * DELETE /api/v1/admin/products/{id}
     * Delete product
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        log.info("Admin: Deleting product: {}", id);

        adminProductService.deleteProduct(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/admin/products/low-stock
     * Get products with low stock
     */
    @GetMapping("/low-stock")
    public ResponseEntity<List<Map<String, Object>>> getLowStockProducts() {
        log.info("Admin: Fetching low stock products");

        List<Map<String, Object>> lowStockItems = adminProductService.getLowStockProducts();

        return ResponseEntity.ok(lowStockItems);
    }

    /**
     * PUT /api/v1/admin/products/variants/{variantId}/stock
     * Update variant stock quantity
     */
    @PutMapping("/variants/{variantId}/stock")
    public ResponseEntity<Void> updateVariantStock(
            @PathVariable UUID variantId,
            @RequestParam Integer stock) {

        log.info("Admin: Updating variant stock: {}, new stock: {}", variantId, stock);

        adminProductService.updateVariantStock(variantId, stock);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/v1/admin/products/statistics
     * Get product statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getProductStatistics() {
        log.info("Admin: Fetching product statistics");

        Map<String, Object> stats = adminProductService.getProductStatistics();

        return ResponseEntity.ok(stats);
    }
}