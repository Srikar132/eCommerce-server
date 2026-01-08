package com.nala.armoire.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.DesignCategoryDTO;
import com.nala.armoire.model.dto.response.DesignDTO;
import com.nala.armoire.model.dto.response.DesignFilterDTO;
import com.nala.armoire.model.dto.response.DesignListDTO;
import com.nala.armoire.model.entity.Design;
import com.nala.armoire.model.entity.DesignCategory;
import com.nala.armoire.repository.DesignCategoryRepository;
import com.nala.armoire.repository.DesignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesignService {

    private final DesignRepository designRepository;
    private final DesignCategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    // ==================== DESIGN CATEGORY METHODS ====================

    @Cacheable(value = "designCategories", key = "'all'")
    @Transactional(readOnly = true)
    public List<DesignCategoryDTO> getAllCategories() {
        log.info("Fetching all active design categories");

        List<DesignCategory> categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();

        return categories.stream()
                .map(this::convertToCategoryDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "designCategory", key = "#slug")
    @Transactional(readOnly = true)
    public DesignCategoryDTO getCategoryBySlug(String slug) {
        log.info("Fetching design category by slug: {}", slug);

        DesignCategory category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Design category not found: "+slug));

        return convertToCategoryDTO(category);

    }

    // ==================== DESIGN METHODS ====================

    @Cacheable(value = "design", key = "#designId")
    @Transactional(readOnly = true)
    public DesignDTO getDesignById(UUID designId) {
        log.info("Fetching design by Id: {}", designId);

        Design design = designRepository.findByIdWithCategory(designId)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + designId));

        return convertToDesignDTO(design);
    }

    @Transactional(readOnly = true)
    public Page<DesignListDTO> getAllDesigns(Integer page, Integer size, String sortBy, String sortDirection) {
        log.info("Fetching all designs - page: {}, size: {}", page, size);

        Pageable pageable = createPageable(page, size, sortBy, sortDirection);
        Page<Design> designs = designRepository.findByIsActiveTrue(pageable);

        return designs.map(this::convertToDesignListDto);
    }

    @Transactional(readOnly = true)
    public Page<DesignListDTO> getDesignsByCategory(UUID categoryId, Integer page, Integer size) {
        log.info("Fetching designs for category: {}", categoryId);

        // Verify category exists
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Design> designs = designRepository.findByCategoryId(categoryId, pageable);

        return designs.map(this::convertToDesignListDto);
    }

    @Transactional(readOnly = true)
    public Page<DesignListDTO> getDesignsByCategorySlug(String slug, Integer page, Integer size) {
        log.info("Fetching designs for category slug: {}", slug);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Design> designs = designRepository.findByCategorySlug(slug, pageable);

        return designs.map(this::convertToDesignListDto);
    }

    @Transactional(readOnly = true)
    public Page<DesignListDTO> searchDesigns(String searchTerm, Integer page, Integer size) {
        log.info("Searching designs with term: {}", searchTerm);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "downloadCount"));
        Page<Design> designs = designRepository.searchDesigns(searchTerm, pageable);

        return designs.map(this::convertToDesignListDto);
    }

    @Transactional(readOnly = true)
    public Page<DesignListDTO> getCompatibleDesigns(String productSlug, Integer page, Integer size) {
        log.info("Fetching compatible designs for product slug: {}", productSlug);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Design> designs = designRepository.findCompatibleDesignsByProductSlug(productSlug, pageable);

        return designs.map(this::convertToDesignListDto);
    }

    @Transactional(readOnly = true)
    public Page<DesignListDTO> filterDesigns(DesignFilterDTO filter) {
        log.info("Filtering designs with filter: {}", filter);

        Pageable pageable = createPageable(
                filter.getPage(),
                filter.getSize(),
                filter.getSortBy(),
                filter.getSortDirection()
        );

        // Get first product type if multiple
        String productType = (filter.getProductTypes() != null && !filter.getProductTypes().isEmpty())
                ? filter.getProductTypes().get(0)
                : null;

        Page<Design> designs = designRepository.findWithFilters(
                filter.getCategoryId(),
                filter.getSearchTerm(),
                filter.getIsPremium(),
                productType,
                pageable
        );

        return designs.map(this::convertToDesignListDto);
    }

    private Pageable createPageable(Integer page, Integer size, String sortBy, String sortDirection) {
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    // ===== Helper methods =======

    private DesignCategoryDTO convertToCategoryDTO(DesignCategory category) {
        return DesignCategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .designCount(category.getDesigns() != null ? category.getDesigns().size() : 0)
                .createdAt(category.getCreatedAt())
                .build();
    }

    private DesignDTO convertToDesignDTO(Design design) {
        return DesignDTO.builder()
                .id(design.getId())
                .categoryId(design.getCategory().getId())
                .categoryName(design.getCategory().getName())
                .name(design.getName())
                .imageUrl(design.getDesignImageUrl())
                .thumbnailUrl(design.getThumbnailUrl())
                .tags(parseTagsFromString(design.getTags()))
                .allowedProductTypes(parseProductTypesFromJson(design.getAllowedProductTypes()))
                .isActive(design.getIsActive())
                .isPremium(design.getIsPremium())
                .downloadCount(design.getDownloadCount())
                .createdAt(design.getCreatedAt())
                .build();
    }

    private DesignListDTO convertToDesignListDto(Design design) {
        return DesignListDTO.builder()
                .id(design.getId())
                .name(design.getName())
                .thumbnailUrl(design.getThumbnailUrl())
                .tags(parseTagsFromString(design.getTags()))
                .isPremium(design.getIsPremium())
                .build();
    }

    private List<String> parseTagsFromString(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(tags.split(",")).stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> parseProductTypesFromJson(String json) {
        if (json == null || json.isBlank()) {
            return null; // null means compatible with all products
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.error("Error parsing product types JSON: {}", json, e);
            return new ArrayList<>();
        }
    }
}
