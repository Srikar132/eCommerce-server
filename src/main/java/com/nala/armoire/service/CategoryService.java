package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.CategoryDTO;
import com.nala.armoire.model.entity.Category;
import com.nala.armoire.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Unified method to get categories with flexible filtering
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "T(java.util.Objects).hash(#slug, #includeChildren, #recursive, #minimal, #includeInactive, #includeProductCount)")
    public List<CategoryDTO> getCategories(
            String slug,
            Boolean includeChildren,
            Boolean recursive,
            Boolean minimal,
            Boolean includeInactive,
            Boolean includeProductCount
    ) {
        List<Category> categories;
        Category parentCategory = null;

        if (slug != null && !slug.isBlank()) {
            // Get specific category and its children
            parentCategory = categoryRepository.findBySlugAndIsActiveTrue(slug)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));

            categories = includeInactive
                    ? categoryRepository.findByParentIdOrderByDisplayOrderAsc(parentCategory.getId())
                    : categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(parentCategory.getId());
        } else {
            // Get root categories
            categories = includeInactive
                    ? categoryRepository.findByParentIsNullOrderByDisplayOrderAsc()
                    : categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
        }

        // Store parent for path building
        final Category parent = parentCategory;

        // Map to DTOs based on requirements
        if (recursive) {
            return categories.stream()
                    .map(cat -> mapRecursive(cat, minimal, includeProductCount, includeInactive))
                    .collect(Collectors.toList());
        } else if (includeChildren) {
            return categories.stream()
                    .map(cat -> mapWithChildren(cat, minimal, includeProductCount, includeInactive))
                    .collect(Collectors.toList());
        } else {
            return categories.stream()
                    .map(cat -> {
                        CategoryDTO dto = mapToDTO(cat, minimal, includeProductCount);
                        // If querying by slug, the parent category itself should also be included with path
                        if (parent != null && cat.getId().equals(parent.getId())) {
                            dto.setFullPath(buildPath(cat));
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get single category by slug with flexible options
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "categoryBySlug", key = "T(java.util.Objects).hash(#slug, #includeChildren, #includeProductCount, #includePath)")
    public CategoryDTO getCategoryBySlug(
            String slug,
            Boolean includeChildren,
            Boolean includeProductCount,
            Boolean includePath
    ) {
        Category category = categoryRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        CategoryDTO dto = mapToDTO(category, false, includeProductCount);

        if (includeChildren) {
            List<Category> subCategories = categoryRepository
                    .findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(category.getId());

            dto.setSubCategories(subCategories.stream()
                    .map(cat -> mapToDTO(cat, false, includeProductCount))
                    .collect(Collectors.toList()));
        }

        if (includePath) {
            dto.setFullPath(buildPath(category));
        }

        return dto;
    }

    /**
     * Map category with direct children only
     */
    private CategoryDTO mapWithChildren(
            Category category,
            Boolean minimal,
            Boolean includeProductCount,
            Boolean includeInactive
    ) {
        CategoryDTO dto = mapToDTO(category, minimal, includeProductCount);

        List<Category> children = includeInactive
                ? categoryRepository.findByParentIdOrderByDisplayOrderAsc(category.getId())
                : categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(category.getId());

        dto.setSubCategories(children.stream()
                .map(cat -> mapToDTO(cat, minimal, includeProductCount))
                .collect(Collectors.toList()));

        return dto;
    }

    /**
     * Map category recursively with all descendants
     */
    private CategoryDTO mapRecursive(
            Category category,
            Boolean minimal,
            Boolean includeProductCount,
            Boolean includeInactive
    ) {
        CategoryDTO dto = mapToDTO(category, minimal, includeProductCount);

        List<Category> children = includeInactive
                ? categoryRepository.findByParentIdOrderByDisplayOrderAsc(category.getId())
                : categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(category.getId());

        if (!children.isEmpty()) {
            dto.setSubCategories(children.stream()
                    .map(cat -> mapRecursive(cat, minimal, includeProductCount, includeInactive))
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    /**
     * Base mapping with conditional fields
     */
    private CategoryDTO mapToDTO(
            Category category,
            Boolean minimal,
            Boolean includeProductCount
    ) {
        // Minimal response: only id, name, slug
        if (minimal) {
            return CategoryDTO.builder()
                    .id(category.getId())
                    .name(category.getName())
                    .slug(category.getSlug())
                    .build();
        }

        // Full response
        CategoryDTO.CategoryDTOBuilder builder = CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt());

        // Add product count if requested
        if (includeProductCount) {
            builder.productCount(categoryRepository.countProductsByCategoryId(category.getId()));
        }

        return builder.build();
    }

    /**
     * Build full category path
     */
    private String buildPath(Category category) {
        List<String> parts = new ArrayList<>();
        Category current = category;

        while (current != null) {
            parts.add(0, current.getName());
            current = current.getParent();
        }

        return String.join(" / ", parts);
    }
}