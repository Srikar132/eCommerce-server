package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.DesignDTO;
import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.model.entity.Design;
import com.nala.armoire.service.AdminDesignService;
import jakarta.validation.Valid;
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

import java.util.UUID;

/**
 * Admin Design Management Controller
 * Requires ADMIN role for all operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/designs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDesignController {

    private final AdminDesignService adminDesignService;

    /**
     * GET /api/v1/admin/designs
     * Get all designs (including inactive)
     * 
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @param sortBy Sort field (default: createdAt)
     * @param sortDir Sort direction (default: DESC)
     * @return Paged response of designs
     */
    @GetMapping
    public ResponseEntity<PagedResponse<DesignDTO>> getAllDesigns(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.info("Admin: Fetching all designs - page: {}, size: {}, sortBy: {}, sortDir: {}", 
                page, size, sortBy, sortDir);

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<DesignDTO> designs = adminDesignService.getAllDesigns(pageable);

        PagedResponse<DesignDTO> response = PagedResponse.<DesignDTO>builder()
                .content(designs.getContent())
                .page(designs.getNumber())
                .size(designs.getSize())
                .totalElements(designs.getTotalElements())
                .totalPages(designs.getTotalPages())
                .first(designs.isFirst())
                .last(designs.isLast())
                .hasNext(designs.hasNext())
                .hasPrevious(designs.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/designs/{id}
     * Get design by ID
     * 
     * @param id Design UUID
     * @return Design details
     */
    @GetMapping("/{id}")
    public ResponseEntity<DesignDTO> getDesignById(@PathVariable UUID id) {
        log.info("Admin: Fetching design: {}", id);

        DesignDTO design = adminDesignService.getDesignById(id);

        return ResponseEntity.ok(design);
    }

    /**
     * POST /api/v1/admin/designs
     * Create new design
     * 
     * @param design Design data
     * @return Created design
     */
    @PostMapping
    public ResponseEntity<DesignDTO> createDesign(@Valid @RequestBody Design design) {
        log.info("Admin: Creating new design: {}", design.getName());

        DesignDTO createdDesign = adminDesignService.createDesign(design);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdDesign);
    }

    /**
     * PUT /api/v1/admin/designs/{id}
     * Update existing design (full update)
     * 
     * @param id Design UUID
     * @param design Updated design data
     * @return Updated design
     */
    @PutMapping("/{id}")
    public ResponseEntity<DesignDTO> updateDesign(
            @PathVariable UUID id,
            @Valid @RequestBody Design design) {

        log.info("Admin: Updating design: {}", id);

        DesignDTO updatedDesign = adminDesignService.updateDesign(id, design);

        return ResponseEntity.ok(updatedDesign);
    }

    /**
     * PATCH /api/v1/admin/designs/{id}
     * Partially update design (supports null values for unchanged fields)
     * 
     * @param id Design UUID
     * @param design Partial design data
     * @return Updated design
     */
    @PatchMapping("/{id}")
    public ResponseEntity<DesignDTO> partialUpdateDesign(
            @PathVariable UUID id,
            @RequestBody Design design) {

        log.info("Admin: Partially updating design: {}", id);

        DesignDTO updatedDesign = adminDesignService.updateDesign(id, design);

        return ResponseEntity.ok(updatedDesign);
    }

    /**
     * DELETE /api/v1/admin/designs/{id}
     * Delete design
     * 
     * @param id Design UUID
     * @return No content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDesign(@PathVariable UUID id) {
        log.info("Admin: Deleting design: {}", id);

        adminDesignService.deleteDesign(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/v1/admin/designs/{id}/status
     * Toggle design active/inactive status
     * 
     * @param id Design UUID
     * @return Updated design
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<DesignDTO> toggleDesignStatus(@PathVariable UUID id) {
        log.info("Admin: Toggling design status: {}", id);

        DesignDTO design = adminDesignService.toggleDesignStatus(id);

        return ResponseEntity.ok(design);
    }

    /**
     * POST /api/v1/admin/designs/sync
     * Manually sync all designs to Elasticsearch
     * 
     * @return Success message
     */
    // @PostMapping("/sync")
    // public ResponseEntity<String> syncAllDesigns() {
    //     log.info("Admin: Manual sync of all designs to Elasticsearch");

    //     adminDesignService.syncAllDesignsToElasticsearch();

    //     return ResponseEntity.ok("All designs synced to Elasticsearch successfully");
    // }
}
