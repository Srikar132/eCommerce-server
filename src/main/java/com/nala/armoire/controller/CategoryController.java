package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.CategoryDTO;
import com.nala.armoire.model.dto.response.PagedResponse;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;


    // Returns complete category tree structure
    @GetMapping("/hierarchy")
    public ResponseEntity<List<CategoryDTO>> getCategoryHierarchy(
            @RequestParam(required = false, defaultValue = "false") Boolean includeActive
    ) {
        List<CategoryDTO> hierarchy = categoryService.getCategoryHierarchy(includeActive);
        return ResponseEntity.ok(hierarchy);
    }

    //Returns only top-level categories (Men, women, kids)
    @GetMapping("/root")
    public ResponseEntity<List<CategoryDTO>> getRootCategories(
            @RequestParam(required = false, defaultValue = "false") Boolean includeActive
    ) {
        List<CategoryDTO> rootCategories = categoryService.getRootCategories(includeActive);

        return ResponseEntity.ok(rootCategories);
    }

    //Returns flat-list of all categories
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getAllCategories(
            @RequestParam(required = false, defaultValue = "false") Boolean includeActive
    ) {
        List<CategoryDTO> categories = categoryService.getAllCategories(includeActive);

        return ResponseEntity.ok(categories);
    }

    //Get single category by slug with its direct subcategories
    @GetMapping("/{slug}")
    public ResponseEntity<CategoryDTO> getCategoryBySlug(
            @PathVariable String slug
    ) {
        CategoryDTO category = categoryService.getCategoryBySlug(slug);

        return ResponseEntity.ok(category);
    }

    //Returns: direct child categories of a parent (Men : {T-shirts, shirts, pants})
    @GetMapping("/{id}/subcategories")
    public ResponseEntity<List<CategoryDTO>> getSubcategories(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") Boolean includeActive) {

        List<CategoryDTO> subcategories = categoryService.getSubcategories(id, includeActive);

        return ResponseEntity.ok(subcategories);
    }

    //returns paginated products for a specific category
    @GetMapping("/{slug}/products")
    public ResponseEntity<PagedResponse<ProductDTO>> getProductsByCategory(
            @PathVariable String slug,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProductDTO> pageResult = categoryService.getProductsByCategory(slug, pageable);

        PagedResponse<ProductDTO> response = PagedResponse.<ProductDTO>builder()
                .content(pageResult.getContent())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .hasNext(pageResult.hasNext())
                .hasPrevious(pageResult.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    //Returns category with all its descendant children (not only direct)
    @GetMapping("/{slug}/with-children")
    public ResponseEntity<CategoryDTO> getCategoryWithAllChildren(
            @PathVariable String slug
    ) {
        CategoryDTO category = categoryService.getCategoryWithAllDescendants(slug);
        return ResponseEntity.ok(category);
    }
}
