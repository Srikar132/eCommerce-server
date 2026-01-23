package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.service.DesignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Design Controller
 * Provides search, filter, and retrieval of designs
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/designs")
@RequiredArgsConstructor
public class DesignController {

    private final DesignService designService;

    /**
     * GET /api/v1/designs - Search, filter, and list designs
     * 
     * Supports:
     * - Full-text search (name, description, tags)
     * - Category filtering
     * - Sorting (name, createdAt, designPrice)
     * - Pagination
     * 
     * Examples:
     * - GET /api/v1/designs?page=0&size=20&sortBy=createdAt&sortDir=DESC
     * - GET /api/v1/designs?q=floral&categorySlug=vintage
     * - GET /api/v1/designs?q=summer&sortBy=designPrice&sortDir=ASC
     */
    @GetMapping
    public ResponseEntity<PagedResponse<DesignListDTO>> searchDesigns(
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        log.info("GET /api/v1/designs - categorySlug: {}, q: {}, page: {}, size: {}", 
                categorySlug, q, page, size);

        // Build sort
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        // Search using PostgreSQL
        Page<DesignListDTO> designsPage = designService.searchDesigns(categorySlug, q, pageable);

        PagedResponse<DesignListDTO> response = PagedResponse.<DesignListDTO>builder()
                .content(designsPage.getContent())
                .page(designsPage.getNumber())
                .size(designsPage.getSize())
                .totalElements(designsPage.getTotalElements())
                .totalPages(designsPage.getTotalPages())
                .first(designsPage.isFirst())
                .last(designsPage.isLast())
                .hasNext(designsPage.hasNext())
                .hasPrevious(designsPage.hasPrevious())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/designs/categories - List all design categories
     * Returns all active categories ordered by display order
     */
    @GetMapping("/categories")
    public ResponseEntity<List<DesignCategoryDTO>> getAllCategories() {
        log.info("GET /api/v1/designs/categories");

        List<DesignCategoryDTO> categories = designService.getAllCategories();

        return ResponseEntity.ok(categories);
    }

    /**
     * GET /api/v1/designs/{id} - Get single design by ID (PostgreSQL)
     * Returns full design details including category information
     */
    @GetMapping("/{id}")
    public ResponseEntity<DesignDTO> getDesignById(@PathVariable UUID id) {
        log.info("GET /api/v1/designs/{}", id);

        DesignDTO design = designService.getDesignById(id);

        return ResponseEntity.ok(
                design
        );
    }
}
