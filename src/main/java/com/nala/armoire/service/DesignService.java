package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.DesignCategoryDTO;
import com.nala.armoire.model.dto.response.DesignDTO;
import com.nala.armoire.model.dto.response.DesignListDTO;
import com.nala.armoire.model.entity.Design;
import com.nala.armoire.model.entity.DesignCategory;
import com.nala.armoire.repository.DesignCategoryRepository;
import com.nala.armoire.repository.DesignRepository;
import com.nala.armoire.repository.DesignSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DesignService {

    private final DesignRepository designRepository;
    private final DesignCategoryRepository designCategoryRepository;

    /**
     * Unified search and filter method for designs.
     * 
     * Supports:
     * - Full-text search across name, description, and tags (case-insensitive)
     * - Category filtering by slug
     * - Pagination and sorting via Pageable
     * - Only returns active designs
     * 
     * Uses JPA Specifications for flexible, composable query building.
     * This approach allows efficient single-query execution regardless of filter combinations.
     * 
     * @param categorySlug Optional category slug to filter by
     * @param searchQuery Optional search text for name/description/tags
     * @param pageable Pagination and sorting parameters
     * @return Page of DesignListDTO matching the criteria
     */
    public Page<DesignListDTO> searchDesigns(String categorySlug, String searchQuery, Pageable pageable) {
        log.debug("Searching designs - categorySlug: {}, searchQuery: {}, page: {}, size: {}", 
                categorySlug, searchQuery, pageable.getPageNumber(), pageable.getPageSize());

        // Start with base specification (active designs only)
        Specification<Design> spec = DesignSpecification.isActive();

        // Add category filter if provided
        if (categorySlug != null && !categorySlug.trim().isEmpty()) {
            spec = spec.and(DesignSpecification.hasCategorySlug(categorySlug));
            log.debug("Applied category filter: {}", categorySlug);
        }

        // Add search query filter if provided
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            spec = spec.and(DesignSpecification.searchByQuery(searchQuery));
            log.debug("Applied search query: {}", searchQuery);
        }

        // Execute query with specifications
        Page<Design> designs = designRepository.findAll(spec, pageable);
        
        log.info("Found {} designs (page {}/{})", 
                designs.getTotalElements(), 
                designs.getNumber() + 1, 
                designs.getTotalPages());

        // Map to DTOs
        return designs.map(this::mapToListDTO);
    }

    /**
     * Get a single design by ID.
     * Returns full design details including category information.
     * 
     * @param id Design UUID
     * @return DesignDTO with full details
     * @throws ResourceNotFoundException if design not found
     */
    public DesignDTO getDesignById(UUID id) {
        log.debug("Fetching design by id: {}", id);
        
        Design design = designRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found with id: " + id));
        
        log.info("Found design: {} ({})", design.getName(), id);
        
        return mapToDTO(design);
    }

    /**
     * Get all design categories.
     * Returns all categories ordered by display order.
     * 
     * @return List of DesignCategoryDTO
     */
    public List<DesignCategoryDTO> getAllCategories() {
        log.debug("Fetching all design categories");
        
        List<DesignCategory> categories = designCategoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        
        log.info("Found {} design categories", categories.size());
        
        return categories.stream()
                .map(this::mapToCategoryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Maps Design entity to DesignListDTO for list views.
     */
    private DesignListDTO mapToListDTO(Design design) {
        // Map category to DesignCategoryDTO
        DesignCategoryDTO categoryDTO = null;
        if (design.getCategory() != null) {
            categoryDTO = DesignCategoryDTO.builder()
                    .id(design.getCategory().getId())
                    .name(design.getCategory().getName())
                    .slug(design.getCategory().getSlug())
                    .description(design.getCategory().getDescription())
                    .displayOrder(design.getCategory().getDisplayOrder())
                    .isActive(design.getCategory().getIsActive())
                    .createdAt(design.getCategory().getCreatedAt())
                    .build();
        }

        return DesignListDTO.builder()
                .id(design.getId())
                .name(design.getName())
                .slug(design.getSlug())
                .description(design.getDescription())
                .designPrice(design.getDesignPrice())
                .designImageUrl(design.getDesignImageUrl())
                .thumbnailUrl(design.getThumbnailUrl())
                .tags(parseTags(design.getTags()))
                .isActive(design.getIsActive())
                .category(categoryDTO)
                .createdAt(design.getCreatedAt())
                .updatedAt(design.getUpdatedAt())
                .build();
    }

    /**
     * Maps Design entity to full DesignDTO with all details.
     */
    private DesignDTO mapToDTO(Design design) {
        return DesignDTO.builder()
                .id(design.getId())
                .categoryId(design.getCategory() != null ? design.getCategory().getId() : null)
                .categoryName(design.getCategory() != null ? design.getCategory().getName() : null)
                .name(design.getName())
                .slug(design.getSlug())
                .description(design.getDescription())
                .designPrice(design.getDesignPrice())
                .designImageUrl(design.getDesignImageUrl())
                .thumbnailUrl(design.getThumbnailUrl())
                .tags(parseTags(design.getTags()))
                .isActive(design.getIsActive())
                .createdAt(design.getCreatedAt())
                .updatedAt(design.getUpdatedAt())
                .build();
    }

    /**
     * Maps DesignCategory entity to DesignCategoryDTO.
     */
    private DesignCategoryDTO mapToCategoryDTO(DesignCategory category) {
        return DesignCategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .build();
    }

    /**
     * Parse comma-separated tags string to list.
     * 
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
