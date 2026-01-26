package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.DesignDTO;
import com.nala.armoire.model.entity.Design;
import com.nala.armoire.repository.DesignCategoryRepository;
import com.nala.armoire.repository.DesignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin Design Management Service
 * Handles CRUD operations for designs with admin privileges
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDesignService {

    private final DesignRepository designRepository;
    private final DesignCategoryRepository designCategoryRepository;

    /**
     * Get all designs (including inactive)
     * @param pageable Pagination parameters
     * @return Page of DesignDTO
     */
    @Transactional(readOnly = true)
    public Page<DesignDTO> getAllDesigns(Pageable pageable) {
        log.info("Admin: Fetching all designs - page: {}, size: {}", 
                pageable.getPageNumber(), pageable.getPageSize());
        
        Page<Design> designs = designRepository.findAll(pageable);
        return designs.map(this::mapToDTO);
    }

    /**
     * Get design by ID
     * @param designId Design UUID
     * @return DesignDTO
     */
    @Transactional(readOnly = true)
    public DesignDTO getDesignById(UUID designId) {
        Design design = designRepository.findByIdWithCategory(designId)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found with id: " + designId));
        
        return mapToDTO(design);
    }

    /**
     * Create new design
     * @param design Design entity
     * @return Created DesignDTO
     */
    @Transactional
    public DesignDTO createDesign(Design design) {
        log.info("Admin: Creating new design: {}", design.getName());
        
        // Validate category
        if (design.getCategory() == null || design.getCategory().getId() == null) {
            throw new BadRequestException("Design category is required");
        }

        designCategoryRepository.findById(design.getCategory().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Design category not found with id: " + design.getCategory().getId()));

        // Validate required fields
        if (design.getName() == null || design.getName().isBlank()) {
            throw new BadRequestException("Design name is required");
        }

        if (design.getSlug() == null || design.getSlug().isBlank()) {
            design.setSlug(
                    design.getName()
                            .toLowerCase()
                            .replaceAll("[^a-z0-9]+", "-")
                            .replaceAll("(^-|-$)", "")
            );
        }
        
        if (design.getDesignImageUrl() == null || design.getDesignImageUrl().isBlank()) {
            throw new BadRequestException("Design image URL is required");
        }
        
        if (design.getDesignPrice() == null || design.getDesignPrice() < 0) {
            throw new BadRequestException("Valid design price is required");
        } 

        // Set default values
        if (design.getIsActive() == null) {
            design.setIsActive(true);
        }

        // Save design
        Design savedDesign = designRepository.save(design);
        log.info("Admin: Created design: {} with ID: {}", savedDesign.getName(), savedDesign.getId());

        return mapToDTO(savedDesign);
    }

    /**
     * Update existing design
     * Supports partial updates (null-safe)
     * @param designId Design UUID
     * @param designUpdate Design update data
     * @return Updated DesignDTO
     */
    @Transactional
    public DesignDTO updateDesign(UUID designId, Design designUpdate) {
        log.info("Admin: Updating design: {}", designId);
        
        Design existingDesign = designRepository.findByIdWithCategory(designId)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found with id: " + designId));

        // Update fields (null-safe for partial updates)
        if (designUpdate.getName() != null) {
            existingDesign.setName(designUpdate.getName());
        }
        
        if (designUpdate.getDescription() != null) {
            existingDesign.setDescription(designUpdate.getDescription());
        }
        
        if (designUpdate.getDesignImageUrl() != null) {
            existingDesign.setDesignImageUrl(designUpdate.getDesignImageUrl());
        }
        
        if (designUpdate.getThumbnailUrl() != null) {
            existingDesign.setThumbnailUrl(designUpdate.getThumbnailUrl());
        }
        
        if (designUpdate.getTags() != null) {
            existingDesign.setTags(designUpdate.getTags());
        }
        
        if (designUpdate.getDesignPrice() != null) {
            if (designUpdate.getDesignPrice() < 0) {
                throw new BadRequestException("Design price cannot be negative");
            }
            existingDesign.setDesignPrice(designUpdate.getDesignPrice());
        }
        
        if (designUpdate.getIsActive() != null) {
            existingDesign.setIsActive(designUpdate.getIsActive());
        }

        // Update category if provided
        if (designUpdate.getCategory() != null && designUpdate.getCategory().getId() != null) {
            designCategoryRepository.findById(designUpdate.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Design category not found with id: " + designUpdate.getCategory().getId()));
            existingDesign.setCategory(designUpdate.getCategory());
        }

        Design savedDesign = designRepository.save(existingDesign);

        // Sync to Elasticsearch

        log.info("Admin: Updated design: {}", savedDesign.getId());

        return mapToDTO(savedDesign);
    }

    /**
     * Delete design
     * @param designId Design UUID
     */
    @Transactional
    public void deleteDesign(UUID designId) {
        log.info("Admin: Deleting design: {}", designId);
        
        Design design = designRepository.findById(designId)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found with id: " + designId));

        // Delete from Elasticsearch first

        // Delete from database
        designRepository.delete(design);

        log.info("Admin: Successfully deleted design: {}", designId);
    }

    /**
     * Toggle design active status
     * @param designId Design UUID
     * @return Updated DesignDTO
     */
    @Transactional
    public DesignDTO toggleDesignStatus(UUID designId) {
        log.info("Admin: Toggling design status: {}", designId);
        
        Design design = designRepository.findById(designId)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found with id: " + designId));

        design.setIsActive(!design.getIsActive());
        Design savedDesign = designRepository.save(design);

        // Sync to Elasticsearch

        log.info("Admin: Toggled design status: {}, active: {}",
                savedDesign.getId(), savedDesign.getIsActive());

        return mapToDTO(savedDesign);
    }


    /**
     * Map Design entity to DTO
     * @param design Design entity
     * @return DesignDTO
     */
    private DesignDTO mapToDTO(Design design) {
        return DesignDTO.builder()
                .id(design.getId())
                .categoryId(design.getCategory().getId())
                .categoryName(design.getCategory().getName())
                .name(design.getName())
                .slug(design.getSlug())
                .description(design.getDescription())
                .designImageUrl(design.getDesignImageUrl())
                .thumbnailUrl(design.getThumbnailUrl())
                .tags(parseTags(design.getTags()))
                .designPrice(design.getDesignPrice())
                .isActive(design.getIsActive())
                .createdAt(design.getCreatedAt())
                .updatedAt(design.getUpdatedAt())
                .build();
    }

    /**
     * Parse comma-separated tags string to list
     * @param tags Comma-separated tags
     * @return List of tags
     */
    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }
}
