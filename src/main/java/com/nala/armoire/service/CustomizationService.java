package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.exception.ValidationException;
import com.nala.armoire.model.dto.request.CustomizationRequest;
import com.nala.armoire.model.dto.response.CustomizationDTO;
import com.nala.armoire.model.dto.response.SaveCustomizationResponse;
import com.nala.armoire.model.entity.Customization;
import com.nala.armoire.model.entity.Design;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.model.entity.ProductVariant;
import com.nala.armoire.repository.CustomizationRepository;
import com.nala.armoire.repository.DesignRepository;
import com.nala.armoire.repository.ProductRepository;
import com.nala.armoire.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomizationService {

    private final CustomizationRepository customizationRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final DesignRepository designRepository;

    @Transactional(readOnly = true)
    public CustomizationDTO getCustomizationById(String customizationId, UUID userId) {
        log.info("Fetching customization: {} for user: {}", customizationId, userId);

        Customization customization = customizationRepository.findByCustomizationId(customizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Customization not found: " + customizationId));

        // Security check - ensure user owns this customization or it's a guest session
        if (userId != null && customization.getUserId() != null &&
                !customization.getUserId().equals(userId)) {
            throw new ValidationException("Access denied to customization");
        }

        return convertToDto(customization);
    }

    @Transactional(readOnly = true)
    public List<CustomizationDTO> getUserCustomizationsForProduct(UUID userId, UUID productId) {
        log.info("Fetching customizations for user: {}, product: {}", userId, productId);

        List<Customization> customizations = customizationRepository
                .findByUserIdAndProductId(userId, productId);

        return customizations.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CustomizationDTO> getUserCustomizations(UUID userId, Integer page, Integer size) {
        log.info("Fetching all customizations for user: {}", userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<Customization> customizations = customizationRepository
                .findByUserIdOrderByUpdatedAtDesc(userId, pageable);

        return customizations.map(this::convertToDto);
    }

    @Transactional
    public SaveCustomizationResponse saveCustomization(CustomizationRequest request, UUID userId) {
        log.info("Saving customization for product: {}, user: {}", request.getProductId(), userId);

        // Validate product exists
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));

        // Validate variant exists and belongs to product
        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + request.getVariantId()));

        if (!variant.getProduct().getId().equals(request.getProductId())) {
            throw new ValidationException("Variant does not belong to the specified product");
        }

        // Validate design exists
        Design design = designRepository.findById(request.getDesignId())
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + request.getDesignId()));

        // Validate thread color hex format (already validated by @Pattern but double-check)
        if (!request.getThreadColorHex().matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ValidationException("Invalid thread color hex format");
        }

        // Validate preview URL
        validatePreviewUrl(request.getPreviewImageUrl());

        // Check if updating existing customization or creating new one
        Customization customization;
        boolean isUpdate = false;

        if (request.getCustomizationId() != null && !request.getCustomizationId().isBlank()) {
            // Update existing customization
            customization = customizationRepository.findByCustomizationId(request.getCustomizationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customization not found: " + request.getCustomizationId()));

            // Security check
            if (userId != null && customization.getUserId() != null &&
                    !customization.getUserId().equals(userId)) {
                throw new ValidationException("Access denied to customization");
            }

            // Update fields
            customization.setProductId(request.getProductId());
            customization.setVariantId(request.getVariantId());
            customization.setDesignId(request.getDesignId());
            customization.setThreadColorHex(request.getThreadColorHex());
            customization.setPreviewImageUrl(request.getPreviewImageUrl());
            customization.setIsCompleted(true);

            isUpdate = true;
            log.info("Updating existing customization: {}", customization.getCustomizationId());
        } else {
            // Create new customization
            String customizationId = UUID.randomUUID().toString();

            customization = Customization.builder()
                    .customizationId(customizationId)
                    .userId(userId)
                    .sessionId(request.getSessionId())
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .designId(request.getDesignId())
                    .threadColorHex(request.getThreadColorHex())
                    .previewImageUrl(request.getPreviewImageUrl())
                    .isCompleted(true)
                    .build();

            log.info("Creating new customization: {}", customizationId);
        }

        customizationRepository.save(customization);

        log.info("Customization saved successfully: {}", customization.getCustomizationId());

        return SaveCustomizationResponse.builder()
                .customizationId(customization.getCustomizationId())
                .previewImageUrl(customization.getPreviewImageUrl())
                
                .createdAt(customization.getCreatedAt())
                .updatedAt(customization.getUpdatedAt())
                .isUpdate(isUpdate)
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<CustomizationDTO> getLatestCustomizationForProduct(UUID userId, UUID productId) {
        log.info("Fetching latest customization for user: {}, product: {}", userId, productId);

        return customizationRepository.findTopByUserIdAndProductIdOrderByUpdatedAtDesc(userId, productId)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<CustomizationDTO> getGuestCustomizationsForProduct(String sessionId, UUID productId) {
        log.info("Fetching guest customizations for session: {}, product: {}", sessionId, productId);

        List<Customization> customizations = customizationRepository
                .findBySessionIdAndProductId(sessionId, productId);

        return customizations.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteCustomization(String customizationId, UUID userId) {
        log.info("Deleting customization: {} for user: {}", customizationId, userId);

        Customization customization = customizationRepository.findByCustomizationId(customizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Customization not found: " + customizationId));

        // Security check
        if (userId != null && customization.getUserId() != null &&
                !customization.getUserId().equals(userId)) {
            throw new ValidationException("Access denied to customization");
        }

        customizationRepository.delete(customization);
        log.info("Customization deleted successfully: {}", customizationId);
    }

    private CustomizationDTO convertToDto(Customization customization) {
        return CustomizationDTO.builder()
                .customizationId(customization.getCustomizationId())
                .userId(customization.getUserId())
                .sessionId(customization.getSessionId())
                .productId(customization.getProductId())
                .variantId(customization.getVariantId())
                .designId(customization.getDesignId())
                .threadColorHex(customization.getThreadColorHex())
                .previewImageUrl(customization.getPreviewImageUrl())
                .isCompleted(customization.getIsCompleted())
                .createdAt(customization.getCreatedAt())
                .updatedAt(customization.getUpdatedAt())
                .build();
    }

    private void validatePreviewUrl(String previewUrl) {
        if (previewUrl == null || previewUrl.isBlank()) {
            throw new ValidationException("Preview image URL is required. Please generate preview in frontend.");
        }

        // Validate URL format (basic check)
        if (!previewUrl.startsWith("https://") && !previewUrl.startsWith("http://")) {
            throw new ValidationException("Invalid preview image URL format");
        }
    }
}
