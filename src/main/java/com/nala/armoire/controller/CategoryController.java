package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.CategoryDTO;
import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * GET /api/v1/categories - List all categories
     */
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getCategories(
            @RequestParam(required = false, defaultValue = "false") Boolean includeInactive
    ) {
        List<CategoryDTO> categories = categoryService.getAllCategories(includeInactive);
        return ResponseEntity.ok(categories);
    }

    /**
     * GET /api/v1/categories/:slug/products - Get products by category
     */
    @GetMapping("/{slug}/products")
    public ResponseEntity<Page<ProductDTO>> getProductsByCategory(
            @PathVariable String slug,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProductDTO> products = categoryService.getProductsByCategory(slug, pageable);
        return ResponseEntity.ok(products);
    }
}
