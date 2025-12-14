package com.nala.armoire.controller;

import com.nala.armoire.model.dto.request.CustomizationRequest;
import com.nala.armoire.model.dto.request.ValidateRequest;
import com.nala.armoire.model.dto.response.ApiResponse;
import com.nala.armoire.model.dto.response.SaveCustomizationResponse;
import com.nala.armoire.model.dto.response.ValidateResponse;
import com.nala.armoire.service.CustomizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/customization")
@RequiredArgsConstructor
public class CustomizationController {

    private final CustomizationService customizationService;

    /**
     * POST /api/customization/validate - Validate customization
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ValidateResponse>> validateCustomization(
            @RequestBody ValidateRequest request) {

        log.info("POST /api/customization/validate - product: {}", request.getProductId());

        ValidateResponse validation = customizationService.validateConfiguration(
                request.getProductId(),
                request.getConfiguration()
        );

        return ResponseEntity.ok(ApiResponse.success(validation, "Validation completed"));
    }

    /**
     * POST /api/customization/save - Save customization
     */
//    @PostMapping("/save")
//    public ResponseEntity<ApiResponse<SaveCustomizationResponse>> saveCustomization(
//            @RequestBody CustomizationRequest request,
//            Authentication authentication) {
//
//        log.info("POST /api/customization/save - product: {}", request.getProductId());
//
//        UUID userId = getUserIdFromAuth(authentication);
//        SaveCustomizationResponse response = customizationService.saveCustomization(request, userId);
//
//        return ResponseEntity.ok(ApiResponse.success(response, "Customization saved successfully"));
//    }
}
