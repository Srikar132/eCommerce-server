package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    //GET /api/v1/search - Search products

    @GetMapping
    public ResponseEntity<Page<ProductDTO>> searchProducts(
            @RequestParam String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProductDTO> products = searchService.searchProducts(query, category, brand, pageable);
        return ResponseEntity.ok(products);
    }
}
