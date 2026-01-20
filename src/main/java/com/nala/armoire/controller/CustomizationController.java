package com.nala.armoire.controller;

import com.nala.armoire.model.dto.request.CustomizationRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.security.UserPrincipal;
import com.nala.armoire.service.CustomizationService;
import com.nala.armoire.service.S3ImageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/customization")
@RequiredArgsConstructor
public class CustomizationController {

    private final CustomizationService customizationService;
    private final S3ImageService s3ImageService;

    /**
     * POST /api/v1/customization/upload-preview
     * Uploads customization preview image to S3
     * Used in: Customizer page - Before saving design
     * 
     * Frontend workflow:
     * 1. User clicks "Save Design"
     * 2. Generate preview image using Konva.toDataURL()
     * 3. Convert to File/Blob
     * 4. Upload using this endpoint → Get preview URL
     * 5. Send preview URL + design data to /save endpoint
     * 
     * Note: Generates temporary UUID for file naming
     * REQUIRES AUTHENTICATION
     */
    @PostMapping("/upload-preview")
    public ResponseEntity<ImageUploadResponse> uploadPreview(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("POST /api/v1/customization/upload-preview - user={}", 
                userPrincipal.getEmail());

        UUID userId = userPrincipal.getId();
        
        // Generate a temporary customization ID for file naming
        UUID tempCustomizationId = UUID.randomUUID();
        
        ImageUploadResponse response = s3ImageService.uploadCustomizationPreview(
            
                file, userId, tempCustomizationId);

        log.info("Preview uploaded successfully: url={}", response.getImageUrl());
        return ResponseEntity.ok(response);
    }    
    
    
    /**
     * POST /api/v1/customization/save
     * Saves or updates customization with frontend-generated preview image
     * Used in: Customizer page - "Save Design" button
     *
     * Frontend must:
     * 1. Generate preview image using Konva/Canvas
     * 2. Upload to S3/CloudFront
     * 3. Send URL in request along with design details
     * 
     * REQUIRES AUTHENTICATION
     */
    @PostMapping("/save")
    public ResponseEntity<SaveCustomizationResponse> saveCustomization(
            @Valid @RequestBody CustomizationRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("POST /api/v1/customization/save - user={}, productId={}", 
                userPrincipal.getEmail(),
                request.getProductId());

        UUID userId = userPrincipal.getId();
        
        SaveCustomizationResponse response = customizationService.saveCustomization(request, userId);

        log.info("Customization saved: customizationId={}, isUpdate={}", 
                response.getId(), response.getIsUpdate());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/customization/{customizationId}
     * Retrieves a specific customization by ID
     * Used in:
     * - Customizer page - Load saved design
     * - Order page - Display customization being ordered
     * - My Designs page - View design details
     * 
     * REQUIRES AUTHENTICATION
     */
    @GetMapping("/{customizationId}")
    public ResponseEntity<CustomizationDTO> getCustomization(
            @PathVariable UUID customizationId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("GET /api/v1/customization/{} - user={}", 
                customizationId, 
                userPrincipal.getEmail());

        UUID userId = userPrincipal.getId();
        CustomizationDTO customization = customizationService.getCustomizationById(customizationId, userId);
        


        return ResponseEntity.ok(customization);
    }

    /**
     * GET /api/v1/customization/product/{productId}
     * Gets all customizations for a specific product by current user
     * Used in:
     * - Customizer page - "Load Previous Design" dropdown
     * - Product page - Show user's existing designs for this product
     * 
     * REQUIRES AUTHENTICATION
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<CustomizationDTO>> getProductCustomizations(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("GET /api/v1/customization/product/{} - user={}", 
                productId, 
                userPrincipal.getEmail());

        UUID userId = userPrincipal.getId();
        List<CustomizationDTO> customizations = customizationService
                .getUserCustomizationsForProduct(userId, productId);

        return ResponseEntity.ok(customizations);
    }

  
    /**
     * GET /api/v1/customization/my-designs
     * Gets all customizations for the current user (paginated)
     * Used in: "My Designs" page - Display all user's saved designs
     * 
     * REQUIRES AUTHENTICATION
     */
    @GetMapping("/my-designs")
    public ResponseEntity<PagedResponse<CustomizationDTO>> getMyDesigns(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("GET /api/v1/customization/my-designs - user={}, page={}, size={}", 
                userPrincipal.getEmail(), page, size);

        UUID userId = userPrincipal.getId();
        Page<CustomizationDTO> customizations = customizationService
                .getUserCustomizations(userId, page, size);

        return ResponseEntity.ok(toPagedResponse(customizations));
    }



    /**
     * DELETE /api/v1/customization/{customizationId}
     * Deletes a customization
     * Used in: "My Designs" page - Delete design button
     * 
     * REQUIRES AUTHENTICATION
     */
    @DeleteMapping("/{customizationId}")
    public ResponseEntity<Void> deleteCustomization(
            @PathVariable UUID customizationId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("DELETE /api/v1/customization/{} - user={}", 
                customizationId, 
                userPrincipal.getEmail());

        UUID userId = userPrincipal.getId();
        customizationService.deleteCustomization(customizationId, userId);

        return ResponseEntity.ok(null);
    }

    /**
     * Helper method to convert Spring Page to PagedResponse
     */
    private <T> PagedResponse<T> toPagedResponse(Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
