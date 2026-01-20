package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.CategoryDTO;
import com.nala.armoire.model.entity.Category;
import com.nala.armoire.service.AdminCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin Category Management Controller
 * Requires ADMIN role
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    /**
     * GET /api/v1/admin/categories
     * Get all categories (including inactive)
     */
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getAllCategories() {
        log.info("Admin: Fetching all categories");

        List<CategoryDTO> categories = adminCategoryService.getAllCategories();

        return ResponseEntity.ok(categories);
    }

    /**
     * GET /api/v1/admin/categories/{id}
     * Get category by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> getCategoryById(@PathVariable UUID id) {
        log.info("Admin: Fetching category by ID: {}", id);

        CategoryDTO category = adminCategoryService.getCategoryById(id);

        return ResponseEntity.ok(category);
    }

    /**
     * GET /api/v1/admin/categories/root
     * Get root categories (no parent)
     */
    @GetMapping("/root")
    public ResponseEntity<List<CategoryDTO>> getRootCategories() {
        log.info("Admin: Fetching root categories");

        List<CategoryDTO> categories = adminCategoryService.getRootCategories();

        return ResponseEntity.ok(categories);
    }

    /**
     * GET /api/v1/admin/categories/{id}/subcategories
     * Get subcategories of a category
     */
    @GetMapping("/{id}/subcategories")
    public ResponseEntity<List<CategoryDTO>> getSubCategories(@PathVariable UUID id) {
        log.info("Admin: Fetching subcategories for category: {}", id);

        List<CategoryDTO> categories = adminCategoryService.getSubCategories(id);

        return ResponseEntity.ok(categories);
    }

    /**
     * POST /api/v1/admin/categories
     * Create new category
     */
    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(@RequestBody Category category) {
        log.info("Admin: Creating new category: {}", category.getName());

        CategoryDTO createdCategory = adminCategoryService.createCategory(category);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
    }

    /**
     * PUT /api/v1/admin/categories/{id}
     * Update existing category (supports partial updates)
     */
    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(
            @PathVariable UUID id,
            @RequestBody Category category) {

        log.info("Admin: Updating category: {}", id);

        CategoryDTO updatedCategory = adminCategoryService.updateCategory(id, category);

        return ResponseEntity.ok(updatedCategory);
    }

    /**
     * PATCH /api/v1/admin/categories/{id}
     * Partial update for category (same as PUT)
     */
    @PatchMapping("/{id}")
    public ResponseEntity<CategoryDTO> patchCategory(
            @PathVariable UUID id,
            @RequestBody Category category) {

        log.info("Admin: Patching category: {}", id);

        CategoryDTO updatedCategory = adminCategoryService.updateCategory(id, category);

        return ResponseEntity.ok(updatedCategory);
    }

    /**
     * DELETE /api/v1/admin/categories/{id}
     * Delete category
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        log.info("Admin: Deleting category: {}", id);

        adminCategoryService.deleteCategory(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/v1/admin/categories/{id}/status
     * Toggle category active/inactive
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<CategoryDTO> toggleCategoryStatus(@PathVariable UUID id) {
        log.info("Admin: Toggling category status: {}", id);

        CategoryDTO category = adminCategoryService.toggleCategoryStatus(id);

        return ResponseEntity.ok(category);
    }

    /**
     * PUT /api/v1/admin/categories/reorder
     * Reorder categories (update display order)
     * Request body: List of category IDs in desired order
     */
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorderCategories(@RequestBody List<UUID> categoryIds) {
        log.info("Admin: Reordering {} categories", categoryIds.size());

        adminCategoryService.reorderCategories(categoryIds);

        return ResponseEntity.ok().build();
    }
}
