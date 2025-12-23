package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.service.DesignService;
import com.nala.armoire.util.PagedResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/designs")
@RequiredArgsConstructor
public class DesignController {

    private final DesignService designService;

    // Get - List all designs
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DesignListDTO>>> getAllDesigns(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        log.info("GET /api/v1/designs - page: {}, size: {}", page, size);

        Page<DesignListDTO> pageResult =
                designService.getAllDesigns(page, size, sortBy, sortDirection);

        PagedResponse<DesignListDTO> response =
                PagedResponseUtil.fromPage(pageResult);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Designs retrieved successfully")
        );
    }

    //Get -> design details
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DesignDTO>> getDesignById(@PathVariable UUID id) {
        log.info("GET /api/v1/designs/{}", id);

        DesignDTO design = designService.getDesignById(id);

        return ResponseEntity.ok(ApiResponse.success(design, "Design retrieved successfully"));
    }

    // Get -> /api/v1/designs/search
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<DesignListDTO>>> searchDesigns(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        log.info("GET /api/v1/designs/search?q={}", q);

        Page<DesignListDTO> pageResult =
                designService.searchDesigns(q, page, size);

        PagedResponse<DesignListDTO> response =
                PagedResponseUtil.fromPage(pageResult);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Search results")
        );
    }

    /**
     * POST /api/designs/filter - Filter designs with complex criteria
     */
    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<PagedResponse<DesignListDTO>>> filterDesigns(
            @RequestBody DesignFilterDTO filter) {

        log.info("POST /api/v1/designs/filter");

        // 1️⃣ Get Page result from service
        Page<DesignListDTO> pageResult = designService.filterDesigns(filter);

        // 2️⃣ Convert Page -> PagedResponse
        PagedResponse<DesignListDTO> response =
                PagedResponseUtil.fromPage(pageResult);

        // 3️⃣ Wrap with ApiResponse
        return ResponseEntity.ok(
                ApiResponse.success(response, "Filtered designs")
        );
    }

}
