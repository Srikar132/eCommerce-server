package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.exception.ValidationException;
import com.nala.armoire.model.dto.request.CustomizationRequest;
import com.nala.armoire.model.dto.response.CustomizationDTO;
import com.nala.armoire.model.dto.response.SaveCustomizationResponse;
import com.nala.armoire.model.entity.*;
import com.nala.armoire.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * IMPROVED CUSTOMIZATION SERVICE
 * 
 * Key Changes:
 * 1. Only authenticated users can save customizations
 * 2. Smart update detection (reuse preview if no changes)
 * 3. No guest customization saving
 * 4. Clear error messages for unauthorized access
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomizationService {

    private final CustomizationRepository customizationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final DesignRepository designRepository;

    /**
     * Get customization by ID
     * SECURITY: Only owner can access
     */
    @Transactional(readOnly = true)
    public CustomizationDTO getCustomizationById(UUID customizationId, UUID userId) {
        log.info("Fetching customization: {} for user: {}", customizationId, userId);

        // CHANGE: Require authentication
        if (userId == null) {
            throw new ValidationException("Must be logged in to view saved customizations");
        }

        Customization customization = customizationRepository.findById(customizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Customization not found: " + customizationId));

        // Security check
        if (!customization.getUserId().equals(userId)) {
            throw new ValidationException("Access denied to customization");
        }

        return convertToDto(customization);
    }

    /**
     * Get all customizations for a product (for current user)
     */
    @Transactional(readOnly = true)
    public List<CustomizationDTO> getUserCustomizationsForProduct(UUID userId, UUID productId) {
        log.info("Fetching customizations for user: {}, product: {}", userId, productId);

        // CHANGE: Require authentication
        if (userId == null) {
            throw new ValidationException("Must be logged in to view saved customizations");
        }

        List<Customization> customizations = customizationRepository
                .findByUserIdAndProductId(userId, productId);

        return customizations.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all user's customizations (My Designs page)
     */
    @Transactional(readOnly = true)
    public Page<CustomizationDTO> getUserCustomizations(UUID userId, Integer page, Integer size) {
        log.info("Fetching all customizations for user: {}", userId);

        // CHANGE: Require authentication
        if (userId == null) {
            throw new ValidationException("Must be logged in to view saved customizations");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Customization> customizations = customizationRepository
                .findByUserIdOrderByUpdatedAtDesc(userId, pageable);

        return customizations.map(this::convertToDto);
    }

    /**
     * Save or update customization
     * AUTHENTICATED USERS ONLY
     */
    @Transactional
    public SaveCustomizationResponse saveCustomization(CustomizationRequest request, UUID userId) {
        log.info("Saving customization for product: {}, user: {}", request.getProductId(), userId);

        // CHANGE: Require authentication
        if (userId == null) {
            throw new ValidationException("Must be logged in to save customizations. Please sign in to save your designs.");
        }

        // Validate product exists
       productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        // Validate variant exists and belongs to product
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + request.getVariantId()));

        if (!variant.getProduct().getId().equals(request.getProductId())) {
            throw new ValidationException("Variant does not belong to the specified product");
        }

        // Validate design exists
        designRepository.findById(request.getDesignId())
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + request.getDesignId()));

        // Validate thread color hex format
        if (!request.getThreadColorHex().matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ValidationException("Invalid thread color hex format");
        }

        // Validate preview URL
        validatePreviewUrl(request.getPreviewImageUrl());

        Customization customization;
        boolean isUpdate = false;

        // Check if updating existing customization
        if (request.getId() != null) {
            customization = customizationRepository.findById(request.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customization not found: " + request.getId()));

            // Security check - MUST be owner
            if (!customization.getUserId().equals(userId)) {
                throw new ValidationException("Access denied to customization");
            }

            // CHANGE: Smart update - detect what changed
            boolean designChanged = !customization.getDesignId().equals(request.getDesignId()) ||
                                   !customization.getThreadColorHex().equals(request.getThreadColorHex());

            // Update fields
            customization.setProductId(request.getProductId());
            customization.setVariantId(request.getVariantId());
            customization.setDesignId(request.getDesignId());
            customization.setThreadColorHex(request.getThreadColorHex());
            customization.setAdditionalNotes(request.getAdditionalNotes());
            
            // Only update preview if design actually changed
            if (designChanged || !request.getPreviewImageUrl().equals(customization.getPreviewImageUrl())) {
                customization.setPreviewImageUrl(request.getPreviewImageUrl());
                log.info("Updated preview image for customization: {}", customization.getId());
            } else {
                log.info("Reusing existing preview image for customization: {}", customization.getId());
            }

            isUpdate = true;
            log.info("Updating existing customization: {}", customization.getId());
        } else {
            // Create new customization
            customization = Customization.builder()
                    .userId(userId) // ALWAYS set for authenticated users
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .designId(request.getDesignId())
                    .threadColorHex(request.getThreadColorHex())
                    .previewImageUrl(request.getPreviewImageUrl())
                    .additionalNotes(request.getAdditionalNotes())
                    .build();

            log.info("Creating new customization for user: {}", userId);
        }

        customizationRepository.save(customization);

        log.info("Customization saved successfully: {}, isUpdate: {}", 
                customization.getId(), isUpdate);

        return SaveCustomizationResponse.builder()
                .id(customization.getId())
                .previewImageUrl(customization.getPreviewImageUrl())
                .createdAt(customization.getCreatedAt())
                .updatedAt(customization.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }

    /**
     * Delete customization
     * AUTHENTICATED USERS ONLY
     */
    @Transactional
    public void deleteCustomization(UUID customizationId, UUID userId) {
        log.info("Deleting customization: {} for user: {}", customizationId, userId);

        // CHANGE: Require authentication
        if (userId == null) {
            throw new ValidationException("Must be logged in to delete customizations");
        }

        Customization customization = customizationRepository.findById(customizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Customization not found: " + customizationId));

        // Security check - MUST be owner
        if (!customization.getUserId().equals(userId)) {
            throw new ValidationException("Access denied to customization");
        }

        customizationRepository.delete(customization);
        log.info("Customization deleted successfully: {}", customizationId);
    }

    /**
     * REMOVED: getGuestCustomizationsForProduct()
     * Guests cannot save customizations anymore
     */

    /**
     * REMOVED: getLatestCustomizationForProduct()
     * Use getUserCustomizationsForProduct() and get first item instead
     */

    private CustomizationDTO convertToDto(Customization customization) {
        return CustomizationDTO.builder()
                .id(customization.getId())
                .userId(customization.getUserId())
                .productId(customization.getProductId())
                .variantId(customization.getVariantId())
                .designId(customization.getDesignId())
                .threadColorHex(customization.getThreadColorHex())
                .previewImageUrl(customization.getPreviewImageUrl())
                .additionalNotes(customization.getAdditionalNotes())
                .createdAt(customization.getCreatedAt())
                .updatedAt(customization.getUpdatedAt())
                .build();
    }

    private void validatePreviewUrl(String previewUrl) {
        if (previewUrl == null || previewUrl.isBlank()) {
            throw new ValidationException("Preview image URL is required");
        }

        if (!previewUrl.startsWith("https://") && !previewUrl.startsWith("http://")) {
            throw new ValidationException("Invalid preview image URL format");
        }
    }
}