package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.BrandDTO;
import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    //GET /api/v1/brands - List all brands
    @GetMapping
    public ResponseEntity<List<BrandDTO>> getBrands(
            @RequestParam(required = false, defaultValue = "false") Boolean includeInactive
    ) {
        List<BrandDTO> brands = brandService.getAllBrands(includeInactive);
        return ResponseEntity.ok(brands);
    }

    // GET /api/v1/brands/:slug/products - Get products by brand

    @GetMapping("/{slug}/products")
    public ResponseEntity<Page<ProductDTO>> getProductsByBrand(
            @PathVariable String slug,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProductDTO> products = brandService.getProductsByBrand(slug, pageable);
        return ResponseEntity.ok(products);
    }
}

