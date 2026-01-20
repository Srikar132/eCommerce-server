package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.model.entity.DesignCategory;
import com.nala.armoire.service.AdminDesignCategoryService;
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

import java.util.List;
import java.util.UUID;

/**
 * Admin Design Category Controller
 * Manages design categories for admin users
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/design-categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDesignCategoryController {

    private final AdminDesignCategoryService adminDesignCategoryService;

    /**
     * GET /api/v1/admin/design-categories
     * Get all design categories with pagination
     * 
     * @param page Page number (default: 0)
     * @param size Page size (default: 20)
     * @param sortBy Sort field (default: displayOrder)
     * @param sortDir Sort direction (default: ASC)
     * @return Paged response of design categories
     */
    @GetMapping
    public ResponseEntity<PagedResponse<DesignCategory>> getAllCategories(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {

        log.info("Admin: Fetching all design categories - page: {}, size: {}", page, size);

        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<DesignCategory> categories = adminDesignCategoryService.getAllCategories(pageable);

        PagedResponse<DesignCategory> response = PagedResponse.<DesignCategory>builder()
                .content(categories.getContent())
                .page(categories.getNumber())
                .size(categories.getSize())
                .totalElements(categories.getTotalElements())
                .totalPages(categories.getTotalPages())
                .first(categories.isFirst())
                .last(categories.isLast())
                .hasNext(categories.hasNext())
                .hasPrevious(categories.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/design-categories/active
     * Get all active design categories ordered by display order
     * 
     * @return List of active design categories
     */
    @GetMapping("/active")
    public ResponseEntity<List<DesignCategory>> getAllActiveCategories() {
        log.info("Admin: Fetching all active design categories");

        List<DesignCategory> categories = adminDesignCategoryService.getAllActiveCategories();

        return ResponseEntity.ok(categories);
    }

    /**
     * GET /api/v1/admin/design-categories/{id}
     * Get design category by ID
     * 
     * @param id Category ID
     * @return Design category
     */
    @GetMapping("/{id}")
    public ResponseEntity<DesignCategory> getCategoryById(@PathVariable UUID id) {
        log.info("Admin: Fetching design category: {}", id);

        DesignCategory category = adminDesignCategoryService.getCategoryById(id);

        return ResponseEntity.ok(category);
    }

    /**
     * POST /api/v1/admin/design-categories
     * Create new design category
     * 
     * @param category Design category data
     * @return Created design category
     */
    @PostMapping
    public ResponseEntity<DesignCategory> createCategory(
            @Valid @RequestBody DesignCategory category) {

        log.info("Admin: Creating new design category: {}", category.getName());

        DesignCategory created = adminDesignCategoryService.createCategory(category);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/v1/admin/design-categories/{id}
     * Update existing design category
     * 
     * @param id Category ID
     * @param category Updated category data
     * @return Updated design category
     */
    @PutMapping("/{id}")
    public ResponseEntity<DesignCategory> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody DesignCategory category) {

        log.info("Admin: Updating design category: {}", id);

        DesignCategory updated = adminDesignCategoryService.updateCategory(id, category);

        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/v1/admin/design-categories/{id}
     * Delete design category (only if no designs exist)
     * 
     * @param id Category ID
     * @return No content response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        log.info("Admin: Deleting design category: {}", id);

        adminDesignCategoryService.deleteCategory(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/v1/admin/design-categories/{id}/toggle-status
     * Toggle category active/inactive status
     * 
     * @param id Category ID
     * @return Updated design category
     */
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<DesignCategory> toggleCategoryStatus(@PathVariable UUID id) {
        log.info("Admin: Toggling status for design category: {}", id);

        DesignCategory updated = adminDesignCategoryService.toggleCategoryStatus(id);

        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/v1/admin/design-categories/reorder
     * Reorder design categories
     * 
     * @param categoryIds List of category IDs in desired order
     * @return No content response
     */
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorderCategories(
            @RequestBody List<UUID> categoryIds) {

        log.info("Admin: Reordering {} design categories", categoryIds.size());

        adminDesignCategoryService.reorderCategories(categoryIds);

        return ResponseEntity.noContent().build();
    }
}
