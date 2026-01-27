package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.mapper.DesignCategoryMapper;
import com.nala.armoire.model.dto.request.DesignCategoryRequest;
import com.nala.armoire.model.dto.response.DesignCategoryResponse;
import com.nala.armoire.model.entity.DesignCategory;
import com.nala.armoire.repository.DesignCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin Design Category Service
 * Handles CRUD operations for design categories
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDesignCategoryService {

    private final DesignCategoryRepository designCategoryRepository;
    private final DesignCategoryMapper mapper;

    /**
     * Get all design categories with pagination
     */
    @Transactional(readOnly = true)
    public Page<DesignCategoryResponse> getAllCategories(Pageable pageable) {
        log.info("Fetching all design categories - page: {}", pageable.getPageNumber());
        return designCategoryRepository.findAll(pageable)
                .map(category -> {
                    DesignCategoryResponse response = mapper.toResponse(category);
                    // Efficiently get design count
                    Long count = designCategoryRepository.countDesignsByCategoryId(category.getId());
                    response.setDesignCount(count != null ? count.intValue() : 0);
                    return response;
                });
    }

    /**
     * Get all active design categories ordered by display order
     */
    @Transactional(readOnly = true)
    public List<DesignCategoryResponse> getAllActiveCategories() {
        log.info("Fetching all active design categories");
        List<DesignCategory> categories = designCategoryRepository
                .findByIsActiveTrueOrderByDisplayOrderAsc();

        return categories.stream()
                .map(category -> {
                    DesignCategoryResponse response = mapper.toResponse(category);
                    Long count = designCategoryRepository.countDesignsByCategoryId(category.getId());
                    response.setDesignCount(count != null ? count.intValue() : 0);
                    return response;
                })
                .toList();
    }

    /**
     * Get design category by ID
     */
    @Transactional(readOnly = true)
    public DesignCategoryResponse getCategoryById(UUID id) {
        log.info("Fetching design category: {}", id);
        DesignCategory category = findCategoryEntityById(id);
        DesignCategoryResponse response = mapper.toResponse(category);

        // Efficiently get design count
        Long count = designCategoryRepository.countDesignsByCategoryId(id);
        response.setDesignCount(count != null ? count.intValue() : 0);

        return response;
    }

    /**
     * Create new design category
     */
    @Transactional
    public DesignCategoryResponse createCategory(DesignCategoryRequest request) {
        log.info("Creating new design category: {}", request.getName());

        // Validate slug uniqueness
        if (designCategoryRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new BadRequestException("Design category with slug '" + request.getSlug() + "' already exists");
        }

        DesignCategory category = mapper.toEntity(request);

        // Set default display order if not provided
        if (category.getDisplayOrder() == null) {
            long maxOrder = designCategoryRepository.count();
            category.setDisplayOrder((int) maxOrder + 1);
        }

        DesignCategory saved = designCategoryRepository.save(category);
        log.info("Design category created successfully: {}", saved.getId());

        return mapper.toResponse(saved);
    }

    /**
     * Update existing design category
     */
    @Transactional
    public DesignCategoryResponse updateCategory(UUID id, DesignCategoryRequest request) {
        log.info("Updating design category: {}", id);

        DesignCategory existing = findCategoryEntityById(id);

        // Check slug uniqueness if changed
        if (!existing.getSlug().equals(request.getSlug())) {
            designCategoryRepository.findBySlug(request.getSlug())
                    .ifPresent(cat -> {
                        throw new BadRequestException("Design category with slug '" + request.getSlug() + "' already exists");
                    });
        }

        // Update fields using mapper
        mapper.updateEntityFromRequest(existing, request);

        DesignCategory saved = designCategoryRepository.save(existing);
        log.info("Design category updated successfully: {}", saved.getId());

        DesignCategoryResponse response = mapper.toResponse(saved);
        Long count = designCategoryRepository.countDesignsByCategoryId(id);
        response.setDesignCount(count != null ? count.intValue() : 0);

        return response;
    }

    /**
     * Delete design category
     * Only allowed if category has no designs
     */
    @Transactional
    public void deleteCategory(UUID id) {
        log.info("Deleting design category: {}", id);

        DesignCategory category = findCategoryEntityById(id);

        // Check if category has designs using efficient count query
        Long designCount = designCategoryRepository.countDesignsByCategoryId(id);
        if (designCount != null && designCount > 0) {
            throw new BadRequestException(
                    "Cannot delete design category with " + designCount + " designs. " +
                            "Please reassign or delete the designs first."
            );
        }

        designCategoryRepository.delete(category);
        log.info("Design category deleted successfully: {}", id);
    }

    /**
     * Toggle category active status
     */
    @Transactional
    public DesignCategoryResponse toggleCategoryStatus(UUID id) {
        log.info("Toggling status for design category: {}", id);

        DesignCategory category = findCategoryEntityById(id);
        category.setIsActive(!category.getIsActive());

        DesignCategory saved = designCategoryRepository.save(category);
        log.info("Design category status toggled to: {}", saved.getIsActive());

        DesignCategoryResponse response = mapper.toResponse(saved);
        Long count = designCategoryRepository.countDesignsByCategoryId(id);
        response.setDesignCount(count != null ? count.intValue() : 0);

        return response;
    }

    /**
     * Reorder design categories
     */
    @Transactional
    public void reorderCategories(List<UUID> categoryIds) {
        log.info("Reordering {} design categories", categoryIds.size());

        for (int i = 0; i < categoryIds.size(); i++) {
            UUID categoryId = categoryIds.get(i);
            DesignCategory category = findCategoryEntityById(categoryId);
            category.setDisplayOrder(i + 1);
            designCategoryRepository.save(category);
        }

        log.info("Design categories reordered successfully");
    }

    /**
     * Helper method to find category entity by ID
     */
    private DesignCategory findCategoryEntityById(UUID id) {
        return designCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Design category not found with id: " + id));
    }
}