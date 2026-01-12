package com.nala.armoire.controller;

import com.nala.armoire.service.ProductSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller for product sync operations
 * Requires ADMIN role
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductSyncService productSyncService;

    /**
     * POST /api/v1/admin/products/sync
     * Sync all products from PostgreSQL to Elasticsearch
     * Use this for initial setup or full reindex
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncAllProducts() {
        log.info("Admin: Syncing all products to Elasticsearch");
        
        try {
            productSyncService.syncAllProducts();
            return ResponseEntity.ok("Successfully synced all products to Elasticsearch");
        } catch (Exception e) {
            log.error("Failed to sync products", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to sync products: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/admin/products/clear
     * Clear all products from Elasticsearch
     * WARNING: This will delete all product data from Elasticsearch index
     */
    @PostMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> clearAllProducts() {
        log.info("Admin: Clearing all products from Elasticsearch");
        
        try {
            productSyncService.clearAllProducts();
            return ResponseEntity.ok("Successfully cleared all products from Elasticsearch");
        } catch (Exception e) {
            log.error("Failed to clear products", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to clear products: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/admin/products/reindex
     * Clear all products and resync from PostgreSQL (full reindex)
     * This combines clear + sync in one operation
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> reindexAllProducts() {
        log.info("Admin: Starting full reindex (clear + resync)");
        
        try {
            productSyncService.clearAndResyncAllProducts();
            return ResponseEntity.ok("Successfully reindexed all products (cleared and resynced)");
        } catch (Exception e) {
            log.error("Failed to reindex products", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to reindex products: " + e.getMessage());
        }
    }
}
