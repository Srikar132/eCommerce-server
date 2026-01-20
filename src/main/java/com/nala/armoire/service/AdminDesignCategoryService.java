package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
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

    /**
     * Get all design categories with pagination
     */
    public Page<DesignCategory> getAllCategories(Pageable pageable) {
        log.info("Fetching all design categories - page: {}", pageable.getPageNumber());
        return designCategoryRepository.findAll(pageable);
    }

    /**
     * Get all active design categories ordered by display order
     */
    public List<DesignCategory> getAllActiveCategories() {
        log.info("Fetching all active design categories");
        return designCategoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    /**
     * Get design category by ID
     */
    public DesignCategory getCategoryById(UUID id) {
        log.info("Fetching design category: {}", id);
        return designCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Design category not found with id: " + id));
    }

    /**
     * Create new design category
     */
    @Transactional
    public DesignCategory createCategory(DesignCategory category) {
        log.info("Creating new design category: {}", category.getName());

        // Validate slug uniqueness
        if (designCategoryRepository.findBySlug(category.getSlug()).isPresent()) {
            throw new BadRequestException("Design category with slug '" + category.getSlug() + "' already exists");
        }

        // Set default display order if not provided
        if (category.getDisplayOrder() == null) {
            long maxOrder = designCategoryRepository.count();
            category.setDisplayOrder((int) maxOrder + 1);
        }

        DesignCategory saved = designCategoryRepository.save(category);
        log.info("Design category created successfully: {}", saved.getId());

        return saved;
    }

    /**
     * Update existing design category
     */
    @Transactional
    public DesignCategory updateCategory(UUID id, DesignCategory updatedCategory) {
        log.info("Updating design category: {}", id);

        DesignCategory existing = getCategoryById(id);

        // Check slug uniqueness if changed
        if (!existing.getSlug().equals(updatedCategory.getSlug())) {
            designCategoryRepository.findBySlug(updatedCategory.getSlug())
                    .ifPresent(cat -> {
                        throw new BadRequestException("Design category with slug '" + updatedCategory.getSlug() + "' already exists");
                    });
        }

        // Update fields
        existing.setName(updatedCategory.getName());
        existing.setSlug(updatedCategory.getSlug());
        existing.setDescription(updatedCategory.getDescription());
        existing.setDisplayOrder(updatedCategory.getDisplayOrder());

        DesignCategory saved = designCategoryRepository.save(existing);
        log.info("Design category updated successfully: {}", saved.getId());

        return saved;
    }

    /**
     * Delete design category
     * Only allowed if category has no designs
     */
    @Transactional
    public void deleteCategory(UUID id) {
        log.info("Deleting design category: {}", id);

        DesignCategory category = getCategoryById(id);

        // Check if category has designs
        if (!category.getDesigns().isEmpty()) {
            throw new BadRequestException(
                    "Cannot delete design category with " + category.getDesigns().size() + " designs. " +
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
    public DesignCategory toggleCategoryStatus(UUID id) {
        log.info("Toggling status for design category: {}", id);

        DesignCategory category = getCategoryById(id);
        category.setIsActive(!category.getIsActive());

        DesignCategory saved = designCategoryRepository.save(category);
        log.info("Design category status toggled to: {}", saved.getIsActive());

        return saved;
    }

    /**
     * Reorder design categories
     */
    @Transactional
    public void reorderCategories(List<UUID> categoryIds) {
        log.info("Reordering {} design categories", categoryIds.size());

        for (int i = 0; i < categoryIds.size(); i++) {
            UUID categoryId = categoryIds.get(i);
            DesignCategory category = getCategoryById(categoryId);
            category.setDisplayOrder(i + 1);
            designCategoryRepository.save(category);
        }

        log.info("Design categories reordered successfully");
    }
}
