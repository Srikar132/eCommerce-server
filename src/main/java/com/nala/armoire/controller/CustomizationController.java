package com.nala.armoire.controller;

import com.nala.armoire.model.dto.request.CustomizationRequest;
import com.nala.armoire.model.dto.request.ValidateRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.service.CustomizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/customization")
@RequiredArgsConstructor
public class CustomizationController {

    private final CustomizationService customizationService;

    /**
     * POST /api/v1/customization/validate
     * Validates the customization configuration before saving
     * Used in: Customizer page - real-time validation as user adds layers
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ValidateResponse>> validateCustomization(
            @RequestBody ValidateRequest request) {

        log.info("POST /api/v1/customization/validate - product: {}", request.getProductId());

        ValidateResponse validation = customizationService.validateConfiguration(
                request.getProductId(),
                request.getConfiguration()
        );

        return ResponseEntity.ok(ApiResponse.success(validation, "Validation completed"));
    }

    /**
     * POST /api/v1/customization/save
     * Saves or updates customization with frontend-generated preview images
     * Used in: Customizer page - "Save Design" button
     *
     * Frontend must:
     * 1. Generate preview image using Konva/Canvas (full size ~1200x1200px)
     * 2. Generate thumbnail image (smaller ~300x300px)
     * 3. Upload both to S3/CloudFront
     * 4. Send URLs in request along with configuration
     */
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<SaveCustomizationResponse>> saveCustomization(
            @RequestBody CustomizationRequest request,
            Authentication authentication) {

        log.info("POST /api/v1/customization/save - product: {}", request.getProductId());

        UUID userId = getUserIdFromAuth(authentication);
        SaveCustomizationResponse response = customizationService.saveCustomization(request, userId);

        String message = response.getIsUpdate()
                ? "Customization updated successfully"
                : "Customization saved successfully";

        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    /**
     * GET /api/v1/customization/{customizationId}
     * Retrieves a specific customization by ID
     * Used in:
     * - Customizer page - Load saved design
     * - Order page - Display customization being ordered
     * - My Designs page - View design details
     */
    @GetMapping("/{customizationId}")
    public ResponseEntity<ApiResponse<CustomizationDTO>> getCustomization(
            @PathVariable String customizationId,
            Authentication authentication) {

        log.info("GET /api/v1/customization/{} - fetching", customizationId);

        UUID userId = getUserIdFromAuth(authentication);
        CustomizationDTO customization = customizationService.getCustomizationById(customizationId, userId);

        return ResponseEntity.ok(ApiResponse.success(customization, "Customization retrieved"));
    }

    /**
     * GET /api/v1/customization/product/{productId}
     * Gets all customizations for a specific product by current user
     * Used in:
     * - Customizer page - "Load Previous Design" dropdown
     * - Product page - Show user's existing designs for this product
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<CustomizationDTO>>> getProductCustomizations(
            @PathVariable UUID productId,
            Authentication authentication) {

        log.info("GET /api/v1/customization/product/{} - fetching user customizations", productId);

        UUID userId = getUserIdFromAuth(authentication);
        List<CustomizationDTO> customizations = customizationService
                .getUserCustomizationsForProduct(userId, productId);

        return ResponseEntity.ok(ApiResponse.success(customizations,
                "Product customizations retrieved"));
    }

    /**
     * GET /api/v1/customization/product/{productId}/latest
     * Gets the most recent customization for a product
     * Used in: Customizer page - Auto-load last design when reopening customizer
     */
    @GetMapping("/product/{productId}/latest")
    public ResponseEntity<ApiResponse<CustomizationDTO>> getLatestCustomization(
            @PathVariable UUID productId,
            Authentication authentication) {

        log.info("GET /api/v1/customization/product/{}/latest", productId);

        UUID userId = getUserIdFromAuth(authentication);
        Optional<CustomizationDTO> customization = customizationService
                .getLatestCustomizationForProduct(userId, productId);

        return customization
                .map(c -> ResponseEntity.ok(ApiResponse.success(c, "Latest customization retrieved")))
                .orElse(ResponseEntity.ok(ApiResponse.success(null, "No customizations found")));
    }

    /**
     * GET /api/v1/customization/my-designs
     * Gets all customizations for the current user (paginated)
     * Used in: "My Designs" page - Display all user's saved designs
     */
    @GetMapping("/my-designs")
    public ResponseEntity<ApiResponse<Page<CustomizationDTO>>> getMyDesigns(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            Authentication authentication) {

        log.info("GET /api/v1/customization/my-designs - page: {}, size: {}", page, size);

        UUID userId = getUserIdFromAuth(authentication);
        Page<CustomizationDTO> customizations = customizationService
                .getUserCustomizations(userId, page, size);

        return ResponseEntity.ok(ApiResponse.success(customizations, "User designs retrieved"));
    }

    /**
     * GET /api/v1/customization/guest/product/{productId}
     * Gets guest customizations by session ID
     * Used in: Customizer page - Load designs for guest users (before login)
     */
    @GetMapping("/guest/product/{productId}")
    public ResponseEntity<ApiResponse<List<CustomizationDTO>>> getGuestCustomizations(
            @PathVariable UUID productId,
            @RequestParam String sessionId) {

        log.info("GET /api/v1/customization/guest/product/{} - session: {}", productId, sessionId);

        List<CustomizationDTO> customizations = customizationService
                .getGuestCustomizationsForProduct(sessionId, productId);

        return ResponseEntity.ok(ApiResponse.success(customizations,
                "Guest customizations retrieved"));
    }

    /**
     * DELETE /api/v1/customization/{customizationId}
     * Deletes a customization
     * Used in: "My Designs" page - Delete design button
     */
    @DeleteMapping("/{customizationId}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomization(
            @PathVariable String customizationId,
            Authentication authentication) {

        log.info("DELETE /api/v1/customization/{}", customizationId);

        UUID userId = getUserIdFromAuth(authentication);
        customizationService.deleteCustomization(customizationId, userId);

        return ResponseEntity.ok(ApiResponse.success(null, "Customization deleted successfully"));
    }

    /**
     * Helper method to extract user ID from authentication
     * Returns null for guest users
     */
    private UUID getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        try {
            // Assuming authentication.getName() returns user ID as string
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse user ID from authentication: {}", authentication.getName());
            return null;
        }
    }
}