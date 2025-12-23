package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.ApiResponse;
import com.nala.armoire.model.dto.response.DesignCategoryDTO;
import com.nala.armoire.model.dto.response.DesignListDTO;
import com.nala.armoire.model.dto.response.PagedResponse;
import com.nala.armoire.service.DesignService;
import com.nala.armoire.util.PagedResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/design-categories")
@RequiredArgsConstructor
public class DesignCategoryController {

    private final DesignService designService;

    /**
     * GET /api/v1/design-categories - List all categories
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DesignCategoryDTO>>> getAllCategories() {

        log.info("GET /api/v1/design-categories");

        List<DesignCategoryDTO> categories = designService.getAllCategories();

        return ResponseEntity.ok(
                ApiResponse.success(categories, "Categories retrieved successfully")
        );
    }

    @GetMapping("/{slug}/designs")
    public ResponseEntity<ApiResponse<PagedResponse<DesignListDTO>>> getDesignsByCategory(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        log.info(" GET /api/v1/design-categories/{}/designs ", slug);

        // 1️⃣ Fetch Page result from service
        Page<DesignListDTO> pageResult =
                designService.getDesignsByCategorySlug(slug, page, size);

        // 2️⃣ Convert Page -> PagedResponse
        PagedResponse<DesignListDTO> response =
                PagedResponseUtil.fromPage(pageResult);

        // 3️⃣ Wrap with ApiResponse
        return ResponseEntity.ok(
                ApiResponse.success(response, " Designs retrieved successfully ")
        );
    }
}

