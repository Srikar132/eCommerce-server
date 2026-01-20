package com.nala.armoire.service;

import com.nala.armoire.exception.BadRequestException;
import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.CategoryDTO;
import com.nala.armoire.model.entity.Category;
import com.nala.armoire.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin Category Management Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Create new category
     */
    @Transactional
    public CategoryDTO createCategory(Category category) {
        // Validate parent category if specified
        if (category.getParent() != null) {
            categoryRepository.findById(category.getParent().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
        }

        // Check for duplicate slug
        if (categoryRepository.existsBySlug(category.getSlug())) {
            throw new BadRequestException("Category with this slug already exists");
        }

        Category savedCategory = categoryRepository.save(category);

        log.info("Admin: Created category: {}", savedCategory.getName());

        return mapToDTO(savedCategory);
    }

    /**
     * Update existing category (supports partial updates)
     */
    @Transactional
    public CategoryDTO updateCategory(UUID categoryId, Category categoryUpdate) {
        Category existingCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Update fields only if provided
        if (categoryUpdate.getName() != null) {
            existingCategory.setName(categoryUpdate.getName());
        }
        if (categoryUpdate.getSlug() != null && !categoryUpdate.getSlug().equals(existingCategory.getSlug())) {
            // Check for duplicate slug if changing
            if (categoryRepository.existsBySlug(categoryUpdate.getSlug())) {
                throw new BadRequestException("Category with this slug already exists");
            }
            existingCategory.setSlug(categoryUpdate.getSlug());
        }
        if (categoryUpdate.getDescription() != null) {
            existingCategory.setDescription(categoryUpdate.getDescription());
        }
        if (categoryUpdate.getImageUrl() != null) {
            existingCategory.setImageUrl(categoryUpdate.getImageUrl());
        }
        if (categoryUpdate.getDisplayOrder() != 0) {
            existingCategory.setDisplayOrder(categoryUpdate.getDisplayOrder());
        }
        if (categoryUpdate.getIsActive() != null) {
            existingCategory.setIsActive(categoryUpdate.getIsActive());
        }

        // Update parent if changed
        if (categoryUpdate.getParent() != null) {
            // Prevent circular references
            if (categoryUpdate.getParent().getId().equals(categoryId)) {
                throw new BadRequestException("Category cannot be its own parent");
            }
            
            // Validate parent exists
            categoryRepository.findById(categoryUpdate.getParent().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
                    
            existingCategory.setParent(categoryUpdate.getParent());
        }

        Category savedCategory = categoryRepository.save(existingCategory);

        log.info("Admin: Updated category: {}", savedCategory.getId());

        return mapToDTO(savedCategory);
    }

    /**
     * Delete category
     */
    @Transactional
    public void deleteCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Check if category has children
        if (categoryRepository.existsByParentId(categoryId)) {
            throw new BadRequestException("Cannot delete category with subcategories. Please delete or reassign subcategories first.");
        }

        categoryRepository.delete(category);

        log.info("Admin: Deleted category: {}", categoryId);
    }

    /**
     * Toggle category active status
     */
    @Transactional
    public CategoryDTO toggleCategoryStatus(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        category.setIsActive(!category.getIsActive());
        categoryRepository.save(category);

        log.info("Admin: Toggled category status: {}, active: {}",
                category.getId(), category.getIsActive());

        return mapToDTO(category);
    }

    /**
     * Reorder categories (update display order)
     */
    @Transactional
    public void reorderCategories(List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new BadRequestException("Category IDs list cannot be empty");
        }

        for (int i = 0; i < categoryIds.size(); i++) {
            UUID categoryId = categoryIds.get(i);
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));

            category.setDisplayOrder(i);
            categoryRepository.save(category);
        }

        log.info("Admin: Reordered {} categories", categoryIds.size());
    }

    /**
     * Get all categories (including inactive)
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        return categories.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get category by ID
     */
    @Transactional(readOnly = true)
    public CategoryDTO getCategoryById(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return mapToDTO(category);
    }

    /**
     * Get root categories (no parent)
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> getRootCategories() {
        List<Category> categories = categoryRepository.findByParentIsNullOrderByDisplayOrderAsc();
        return categories.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get subcategories of a category
     */
    @Transactional(readOnly = true)
    public List<CategoryDTO> getSubCategories(UUID parentId) {
        List<Category> categories = categoryRepository.findByParentIdOrderByDisplayOrderAsc(parentId);
        return categories.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private CategoryDTO mapToDTO(Category category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
