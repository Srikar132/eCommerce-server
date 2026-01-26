package com.nala.armoire.controller;

import com.nala.armoire.model.dto.request.CategoryRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.service.AdminCategoryService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin controller for category management with comprehensive filtering and pagination
 *
 * Security: All endpoints require ADMIN role
 * Rate Limiting: Applied to state-changing operations
 * Caching: Service layer handles caching strategy
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
@Validated
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    /**
     * Unified category endpoint with flexible filtering for admin
     * <p>
     * Query Parameters:
     *
     * @param slug                - Category slug for filtering (optional)
     * @param includeChildren     - Include direct subcategories (default: false)
     * @param recursive           - Include all descendants recursively (default: false)
     * @param minimal             - Return minimal data (id, name, slug only) (default: false)
     * @param includeInactive     - Include inactive categories (default: true for admin)
     * @param includeProductCount - Include product counts (default: false)
     * @param page                - Page number for pagination (0-based, default: 0)
     * @param size                - Page size (default: 20, max: 100)
     * @param sortBy              - Sort field (name, createdAt, displayOrder, default: displayOrder)
     * @param sortDir             - Sort direction (asc, desc, default: asc)
     *                            <p>
     *                            Examples:
     *                            - Root categories tree: GET /api/v1/admin/categories?recursive=true
     *                            - Paginated roots: GET /api/v1/admin/categories?page=0&size=20
     *                            - Category with children: GET /api/v1/admin/categories?slug=men&includeChildren=true
     *                            - Minimal for dropdowns: GET /api/v1/admin/categories?minimal=true&recursive=true
     *                            - With product counts: GET /api/v1/admin/categories?includeProductCount=true
     * @return List or PagedResponse based on pagination params
     */
    @GetMapping
    public ResponseEntity<?> getCategories(
            @RequestParam(required = false) String slug,
            @RequestParam(defaultValue = "false") Boolean includeChildren,
            @RequestParam(defaultValue = "false") Boolean recursive,
            @RequestParam(defaultValue = "false") Boolean minimal,
            @RequestParam(defaultValue = "true") Boolean includeInactive,
            @RequestParam(defaultValue = "false") Boolean includeProductCount,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "displayOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        log.info("Admin: Fetching categories - slug={}, children={}, recursive={}, minimal={}, inactive={}, productCount={}, page={}, size={}",
                slug, includeChildren, recursive, minimal, includeInactive, includeProductCount, page, size);

        // Determine if pagination is requested
        boolean isPaginated = page != null && size != null;

        if (isPaginated) {
            PagedResponse<CategoryDTO> pagedCategories = adminCategoryService.getCategoriesPaged(
                    slug, includeChildren, recursive, minimal,
                    includeInactive, includeProductCount,
                    page, size, sortBy, sortDir
            );
            log.info("Returning {} categories (page {}/{})",
                    pagedCategories.getContent().size(),
                    pagedCategories.getPage() + 1,
                    pagedCategories.getTotalPages());
            return ResponseEntity.ok(pagedCategories);
        } else {
            List<CategoryDTO> categories = adminCategoryService.getCategories(
                    slug, includeChildren, recursive, minimal,
                    includeInactive, includeProductCount
            );
            log.info("Returning {} categories", categories.size());
            return ResponseEntity.ok(categories);
        }
    }

    /**
     * Get single category details with full hierarchy information
     *
     * @param id                  - Category UUID
     * @param includeProductCount - Include product count (default: false)
     * @return CategoryDTO with hierarchy path
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> getCategory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") Boolean includeProductCount
    ) {
        log.info("Admin: Fetching category {} with productCount={}", id, includeProductCount);
        CategoryDTO category = adminCategoryService.getCategory(id, includeProductCount);
        return ResponseEntity.ok(category);
    }

    /**
     * Create a root category (parentId in request body is ignored)
     *
     * @param request - Category creation request
     * @return Created category with 201 status
     */
    @PostMapping
    @RateLimiter(name = "admin-write")
    public ResponseEntity<CategoryDTO> createRootCategory(@Valid @RequestBody CategoryRequest request) {
        log.info("Admin: Creating root category '{}'", request.getName());
        CategoryDTO created = adminCategoryService.createRootCategory(request);
        log.info("Admin: Created root category with id: {}", created.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Create a subcategory under an existing parent
     *
     * @param parentId - Parent category UUID
     * @param request  - Category creation request
     * @return Created subcategory with 201 status
     */
    @PostMapping("/{parentId}/subcategories")
    @RateLimiter(name = "admin-write")
    public ResponseEntity<CategoryDTO> createSubCategory(
            @PathVariable UUID parentId,
            @Valid @RequestBody CategoryRequest request
    ) {
        log.info("Admin: Creating subcategory '{}' under parent {}", request.getName(), parentId);
        CategoryDTO created = adminCategoryService.createSubCategory(parentId, request);
        log.info("Admin: Created subcategory with id: {}", created.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update a category (supports moving to new parent)
     *
     * @param id      - Category UUID to update
     * @param request - Updated category data
     * @return Updated category
     */
    @PutMapping("/{id}")
    @RateLimiter(name = "admin-write")
    public ResponseEntity<CategoryDTO> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request
    ) {
        log.info("Admin: Updating category {}", id);
        CategoryDTO updated = adminCategoryService.updateCategory(id, request);
        log.info("Admin: Updated category {}", id);
        return ResponseEntity.ok(updated);
    }

    /**
     * Toggle active/inactive status for a category
     *
     * @param id - Category UUID
     * @return Updated category with new status
     */
    @PutMapping("/{id}/toggle-status")
    @RateLimiter(name = "admin-write")
    public ResponseEntity<CategoryDTO> toggleCategoryStatus(@PathVariable UUID id) {
        log.info("Admin: Toggling status for category {}", id);
        CategoryDTO updated = adminCategoryService.toggleStatus(id);
        log.info("Admin: Toggled category {} status to {}", id, updated.getIsActive());
        return ResponseEntity.ok(updated);
    }

    /**
     * Reorder categories by updating display order
     *
     * @param id              - Category UUID
     * @param newDisplayOrder - New display order value
     * @return Updated category
     */
    @PutMapping("/{id}/reorder")
    @RateLimiter(name = "admin-write")
    public ResponseEntity<CategoryDTO> reorderCategory(
            @PathVariable UUID id,
            @RequestParam @Min(0) Integer newDisplayOrder
    ) {
        log.info("Admin: Reordering category {} to position {}", id, newDisplayOrder);
        CategoryDTO updated = adminCategoryService.updateDisplayOrder(id, newDisplayOrder);
        return ResponseEntity.ok(updated);
    }

    /**
     * Bulk update category statuses
     *
     * @param ids    - List of category UUIDs
     * @param active - New active status
     * @return Number of categories updated
     */
    @PutMapping("/bulk/status")
    @RateLimiter(name = "admin-write")
    public ResponseEntity<BulkUpdateResponse> bulkUpdateStatus(
            @RequestParam List<UUID> ids,
            @RequestParam Boolean active
    ) {
        log.info("Admin: Bulk updating {} categories to active={}", ids.size(), active);
        int updated = adminCategoryService.bulkUpdateStatus(ids, active);
        log.info("Admin: Bulk updated {} categories", updated);
        return ResponseEntity.ok(new BulkUpdateResponse(updated, "Status updated successfully"));
    }

    /**
     * Delete a category (only if no subcategories or linked products)
     *
     * @param id    - Category UUID
     * @param force - Force delete (cascade to products) - USE WITH CAUTION (default: false)
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @RateLimiter(name = "admin-write")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") Boolean force
    ) {
        if (force) {
            log.warn("Admin: FORCE deleting category {} (will affect products)", id);
        } else {
            log.info("Admin: Deleting category {}", id);
        }
        adminCategoryService.deleteCategory(id, force);
        log.info("Admin: Successfully deleted category {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get category statistics for dashboard
     *
     * @return Category statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<CategoryStatsResponse> getCategoryStats() {
        log.info("Admin: Fetching category statistics");
        CategoryStatsResponse stats = adminCategoryService.getCategoryStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Validate category slug availability
     *
     * @param slug      - Slug to validate
     * @param excludeId - Category ID to exclude from check (for updates)
     * @return Availability status
     */
    @GetMapping("/validate-slug")
    public ResponseEntity<SlugValidateResponse> validateSlug(
            @RequestParam String slug,
            @RequestParam(required = false) UUID excludeId
    ) {
        log.debug("Admin: Validating slug '{}' excluding {}", slug, excludeId);
        boolean available = adminCategoryService.isSlugAvailable(slug, excludeId);
        return ResponseEntity.ok(new SlugValidateResponse(slug, available));
    }
}