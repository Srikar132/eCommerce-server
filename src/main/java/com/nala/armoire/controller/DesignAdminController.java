package com.nala.armoire.controller;

import com.nala.armoire.service.DesignSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller for design sync operations
 * Requires ADMIN role
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/designs")
@RequiredArgsConstructor
public class DesignAdminController {

    private final DesignSyncService designSyncService;

    /**
     * POST /api/v1/admin/designs/sync
     * Sync all designs from PostgreSQL to Elasticsearch
     * Use this for initial setup or full reindex
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncAllDesigns() {
        log.info("Admin: Syncing all designs to Elasticsearch");
        
        try {
            designSyncService.syncAllDesigns();
            return ResponseEntity.ok("Successfully synced all designs to Elasticsearch");
        } catch (Exception e) {
            log.error("Failed to sync designs", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to sync designs: " + e.getMessage());
        }
    }
}
