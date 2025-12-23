package com.nala.armoire.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.exception.ValidationException;
import com.nala.armoire.model.dto.request.CustomizationRequest;
import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.model.dto.response.CustomizationMetadata;
import com.nala.armoire.model.entity.Customization;
import com.nala.armoire.repository.CustomizationRepository;
import com.nala.armoire.repository.DesignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomizationService {

    private final CustomizationRepository customizationRepository;
    private final DesignRepository designRepository;
    private final ObjectMapper objectMapper;

    // Validation constants
    private static final int MIN_IMAGE_RESOLUTION = 300; // DPI
    private static final int MAX_LAYERS = 20;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("PNG", "JPG", "JPEG");

    //validation
    public ValidateResponse validateConfiguration(UUID productId, CustomizationConfigDTO config) {
        log.info("Validating customization configuration for product: {}", productId);

        List<ValidationError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate canvas
        if (config.getCanvas() == null) {
            errors.add(createError("canvas", "Canvas configuration is required", "CANVAS_REQUIRED"));
        }

        // Validate layers
        if (config.getLayers() == null || config.getLayers().isEmpty()) {
            errors.add(createError("layers", "At least one layer is required", "LAYERS_REQUIRED"));
        } else {
            validateLayers(config.getLayers(), config.getCanvas(), errors, warnings);
        }

        // Check max layers
        if (config.getLayers() != null && config.getLayers().size() > MAX_LAYERS) {
            errors.add(createError("layers",
                    "Maximum " + MAX_LAYERS + " layers allowed",
                    "MAX_LAYERS_EXCEEDED"));
        }

        return ValidateResponse.builder()
                .isValid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    private void validateLayers(List<LayerDTO> layers, CanvasDTO canvas,
                                List<ValidationError> errors, List<String> warnings) {

        for (int i = 0; i < layers.size(); i++) {
            LayerDTO layer = layers.get(i);
            String layerPrefix = "layers[" + i + "]";

            // Validate layer type
            if (layer.getType() == null) {
                errors.add(createError(layerPrefix + ".type", "Layer type is required", "TYPE_REQUIRED"));
                continue;
            }

            // Validate based on type
            switch (layer.getType().toLowerCase()) {
                case "design":
                    validateDesignLayer(layer, layerPrefix, errors);
                    break;
                case "text":
                    validateTextLayer(layer, layerPrefix, errors, warnings);
                    break;
                case "image":
                    validateImageLayer(layer, layerPrefix, errors, warnings);
                    break;
                default:
                    errors.add(createError(layerPrefix + ".type",
                            "Invalid layer type: " + layer.getType(),
                            "INVALID_TYPE"));
            }

            // Validate position and size
            validatePositionAndSize(layer, canvas, layerPrefix, errors, warnings);
        }
    }

    private void validateDesignLayer(LayerDTO layer, String prefix, List<ValidationError> errors) {
        if (layer.getDesignId() == null) {
            errors.add(createError(prefix + ".designId", "Design ID is required", "DESIGN_ID_REQUIRED"));
            return;
        }

        // Check if design exists
        designRepository.findById(layer.getDesignId())
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + layer.getDesignId()));
    }

    private void validateTextLayer(LayerDTO layer, String prefix,
                                   List<ValidationError> errors, List<String> warnings) {
        if (layer.getText() == null || layer.getText().isBlank()) {
            errors.add(createError(prefix + ".text", "Text content is required", "TEXT_REQUIRED"));
        } else if (layer.getText().length() > MAX_TEXT_LENGTH) {
            errors.add(createError(prefix + ".text",
                    "Text exceeds maximum length of " + MAX_TEXT_LENGTH,
                    "TEXT_TOO_LONG"));
        }

        if (layer.getFontSize() != null && layer.getFontSize() < 8) {
            warnings.add("Font size less than 8pt may not be readable when printed");
        }
    }

    private void validateImageLayer(LayerDTO layer, String prefix,
                                    List<ValidationError> errors, List<String> warnings) {
        if (layer.getImageUrl() == null || layer.getImageUrl().isBlank()) {
            errors.add(createError(prefix + ".imageUrl", "Image URL is required", "IMAGE_URL_REQUIRED"));
            return;
        }

        // Add warning for image resolution check
        warnings.add("Please ensure uploaded images have minimum " + MIN_IMAGE_RESOLUTION + " DPI for quality printing");
    }

    private void validatePositionAndSize(LayerDTO layer, CanvasDTO canvas,
                                         String prefix, List<ValidationError> errors, List<String> warnings) {
        if (layer.getPosition() == null) {
            errors.add(createError(prefix + ".position", "Position is required", "POSITION_REQUIRED"));
            return;
        }

        if (layer.getSize() == null) {
            errors.add(createError(prefix + ".size", "Size is required", "SIZE_REQUIRED"));
            return;
        }

        // Check if layer is within canvas bounds
        if (canvas != null && canvas.getSafeZone() != null) {
            SafeZoneDTO safeZone = canvas.getSafeZone();
            PositionDTO pos = layer.getPosition();
            SizeDTO size = layer.getSize();

            if (pos.getX() < safeZone.getX() ||
                    pos.getY() < safeZone.getY() ||
                    pos.getX() + size.getWidth() > safeZone.getX() + safeZone.getWidth() ||
                    pos.getY() + size.getHeight() > safeZone.getY() + safeZone.getHeight()) {

                warnings.add("Layer at index " + prefix + " extends beyond safe print area");
            }
        }
    }

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

        // Update last accessed time
        customizationRepository.updateLastAccessedAt(customization.getId(), LocalDateTime.now());

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

        // 1️⃣ Validate configuration
        ValidateResponse validation = validateConfiguration(
                request.getProductId(),
                request.getConfiguration()
        );

        if (!validation.getIsValid()) {
            throw new ValidationException("Invalid customization configuration", validation.getErrors());
        }

        // 2️ Validate preview URLs from frontend (REQUIRED - frontend generates these)
        validatePreviewUrls(request.getPreviewImageUrl(), request.getThumbnailUrl());

        // 3️ Analyze configuration metadata
        CustomizationMetadata metadata = analyzeConfiguration(request.getConfiguration());

        // 4️ Check if updating existing customization or creating new one
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
            customization.setPreviewImageUrl(request.getPreviewImageUrl());
            customization.setThumbnailUrl(request.getThumbnailUrl());
            customization.setConfigurationJson(convertConfigToJson(request.getConfiguration()));
            customization.setHasText(metadata.hasText);
            customization.setHasDesign(metadata.hasDesign);
            customization.setHasUploadedImage(metadata.hasUploadedImage);
            customization.setLayerCount(metadata.layerCount);
            customization.setIsCompleted(true);
            customization.setLastAccessedAt(LocalDateTime.now());

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
                    .previewImageUrl(request.getPreviewImageUrl())
                    .thumbnailUrl(request.getThumbnailUrl())
                    .configurationJson(convertConfigToJson(request.getConfiguration()))
                    .hasText(metadata.hasText)
                    .hasDesign(metadata.hasDesign)
                    .hasUploadedImage(metadata.hasUploadedImage)
                    .layerCount(metadata.layerCount)
                    .isCompleted(true)
                    .lastAccessedAt(LocalDateTime.now())
                    .build();

            log.info("Creating new customization: {}", customizationId);
        }

        customizationRepository.save(customization);

        log.info("Customization saved successfully: {}", customization.getCustomizationId());

        return SaveCustomizationResponse.builder()
                .customizationId(customization.getCustomizationId())
                .previewUrl(customization.getPreviewImageUrl())
                .thumbnailUrl(customization.getThumbnailUrl())
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

    private CustomizationMetadata analyzeConfiguration(CustomizationConfigDTO config) {
        CustomizationMetadata metadata = new CustomizationMetadata();

        if (config.getLayers() != null) {
            metadata.layerCount = config.getLayers().size();

            for (LayerDTO layer : config.getLayers()) {
                switch (layer.getType().toLowerCase()) {
                    case "text":
                        metadata.hasText = true;
                        break;
                    case "design":
                        metadata.hasDesign = true;
                        break;
                    case "image":
                        metadata.hasUploadedImage = true;
                        break;
                }
            }
        }

        return metadata;
    }

    private ValidationError createError(String field, String message, String errorCode) {
        return ValidationError.builder()
                .field(field)
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    private CustomizationDTO convertToDto(Customization customization) {
        return CustomizationDTO.builder()
                .customizationId(customization.getCustomizationId())
                .productId(customization.getProductId())
                .previewImageUrl(customization.getPreviewImageUrl())
                .thumbnailUrl(customization.getThumbnailUrl())
                .configuration(parseConfigFromJson(customization.getConfigurationJson()))
                .hasText(customization.getHasText())
                .hasDesign(customization.getHasDesign())
                .hasUploadedImage(customization.getHasUploadedImage())
                .layerCount(customization.getLayerCount())
                .isCompleted(customization.getIsCompleted())
                .createdAt(customization.getCreatedAt())
                .updatedAt(customization.getUpdatedAt())
                .build();
    }

    private String convertConfigToJson(CustomizationConfigDTO config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Error converting config to JSON", e);
            throw new RuntimeException("Failed to serialize configuration", e);
        }
    }

    private CustomizationConfigDTO parseConfigFromJson(String json) {
        try {
            return objectMapper.readValue(json, CustomizationConfigDTO.class);
        } catch (Exception e) {
            log.error("Error parsing config JSON", e);
            throw new RuntimeException("Failed to deserialize configuration", e);
        }
    }

    private void validatePreviewUrls(String previewUrl, String thumbnailUrl) {
        if (previewUrl == null || previewUrl.isBlank()) {
            throw new ValidationException("Preview image URL is required. Please generate preview in frontend.");
        }

        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            throw new ValidationException("Thumbnail URL is required. Please generate thumbnail in frontend.");
        }

        // Validate URL format (basic check)
        if (!previewUrl.startsWith("https://") && !previewUrl.startsWith("http://")) {
            throw new ValidationException("Invalid preview image URL format");
        }

        if (!thumbnailUrl.startsWith("https://") && !thumbnailUrl.startsWith("http://")) {
            throw new ValidationException("Invalid thumbnail URL format");
        }
    }
}