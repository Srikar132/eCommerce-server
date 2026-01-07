package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.CategoryDTO;
import com.nala.armoire.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Unified category endpoint with flexible filtering
     *
     * @param slug - Category slug for filtering (optional)
     * @param includeChildren - Include subcategories (default: false)
     * @param recursive - Include all descendants recursively (default: false)
     * @param minimal - Return minimal data for UI components (default: false)
     * @param includeInactive - Include inactive categories (default: false)
     * @param includeProductCount - Include product counts (default: false)

     * Examples:
     * - Root categories minimal: /api/v1/categories?minimal=true
     * - Root categories full: /api/v1/categories
     * - Category children: /api/v1/categories?slug=men&includeChildren=true
     * - Full hierarchy: /api/v1/categories?recursive=true
     * - Category with counts: /api/v1/categories?slug=men&includeChildren=true&includeProductCount=true
     */
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getCategories(
            @RequestParam(required = false) String slug,
            @RequestParam(defaultValue = "false") Boolean includeChildren,
            @RequestParam(defaultValue = "false") Boolean recursive,
            @RequestParam(defaultValue = "false") Boolean minimal,
            @RequestParam(defaultValue = "false") Boolean includeInactive,
            @RequestParam(defaultValue = "false") Boolean includeProductCount
    ) {
        List<CategoryDTO> categories = categoryService.getCategories(
                slug, includeChildren, recursive, minimal,
                includeInactive, includeProductCount
        );
        return ResponseEntity.ok(categories);
    }
}