package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.CategoryDTO;
import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.model.entity.Category;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.repository.CategoryRepository;
import com.nala.armoire.repository.ProductImageRepository;
import com.nala.armoire.repository.ProductRepository;
import com.nala.armoire.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;

    //Get all categories in flat list
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", key = "'all_' + #includeInactive")
    public List<CategoryDTO> getAllCategories(Boolean includeInactive) {
        List<Category> categories;

        if(includeInactive) {
            categories = categoryRepository.findAllWithParent();
        } else {
            categories = categoryRepository.findAllActiveWithParent();
        }

        return categories.stream()
                .map(this::mapToCategoryDTO)
                .collect(Collectors.toList());
    }

    //Get Category hierarchy -> (tree structure)

    @Transactional(readOnly = true)
    @Cacheable(value = "categoryHierarchy", key = "#includeInactive")
    public List<CategoryDTO> getCategoryHierarchy(Boolean includeInactive) {
        List<Category> allCategories = includeInactive
                ? categoryRepository.findAllByOrderByDisplayOrderAsc()
                : categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();

        Map<UUID, CategoryDTO> categoryMap = new HashMap<>();
        List<CategoryDTO> rootCategories = new ArrayList<>();

        // First pass: create DTOs
        for (Category category : allCategories) {
            CategoryDTO dto = mapToCategoryDTO(category);
            dto.setSubCategories(new ArrayList<>());
            categoryMap.put(category.getId(), dto);
        }

        // Second pass: build hierarchy
        for (Category category : allCategories) {
            CategoryDTO dto = categoryMap.get(category.getId());

            if (category.getParent() == null) {
                rootCategories.add(dto);
            } else {
                CategoryDTO parent = categoryMap.get(category.getParent().getId());
                if (parent != null) {
                    parent.getSubCategories().add(dto);
                }
            }
        }

        return rootCategories;
    }

    // Get root categories only (Men, Women, Kids)
    @Transactional(readOnly = true)
    @Cacheable(value = "rootCategories", key = "#includeInactive")
    public List<CategoryDTO> getRootCategories(Boolean includeInactive) {
        List<Category> categories = includeInactive
                ? categoryRepository.findByParentIsNullOrderByDisplayOrderAsc()
                : categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();

        return categories.stream()
                .map(this::mapToCategoryDTO)
                .collect(Collectors.toList());
    }

    // Get a single category by slug with its subcategories
    // used for category landing pages
    @Transactional(readOnly = true)
    @Cacheable(value = "categoryBySlug", key = "#slug")
    public CategoryDTO getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        CategoryDTO dto = mapToCategoryDTOWithCount(category);

        List<Category> subCategories = categoryRepository
                .findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(category.getId());

        List<CategoryDTO> subCategoryDTOs = subCategories.stream()
                .map(this::mapToCategoryDTOWithCount)
                .collect(Collectors.toList());

        dto.setSubCategories(subCategoryDTOs);
        dto.setFullPath(buildCategoryPath(category));

        return dto;
    }

    /**
     * Get subcategories of a parent category
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "subcategories", key = "#parentId + '_' + #includeInactive")
    public List<CategoryDTO> getSubcategories(UUID parentId, Boolean includeInactive) {
        categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));

        List<Category> categories = includeInactive
                ? categoryRepository.findByParentIdOrderByDisplayOrderAsc(parentId)
                : categoryRepository.findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(parentId);

        return categories.stream()
                .map(this::mapToCategoryDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> getProductsByCategory(String categorySlug, Pageable pageable) {
        Category category = categoryRepository.findBySlugAndIsActiveTrue(categorySlug)
                .orElseThrow(() -> new ResourceNotFoundException("Category Not found"));

        Page<Product> products = productRepository.findByCategoryIdAndIsActiveTrue(category.getId(), pageable);

        return products.map(product -> {
            Double averageRating = reviewRepository.findAverageRatingByProductId(product.getId());

            Long reviewCount = reviewRepository.countByProductId(product.getId());

            return ProductDTO.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .slug(product.getSlug())
                    .description(product.getDescription())
                    .basePrice(product.getBasePrice())
                    .sku(product.getSku())
                    .isCustomizable(product.getIsCustomizable())
                    .material(product.getMaterial())
                    .categoryId(product.getCategory().getId())
                    .categoryName(product.getCategory().getName())
                    .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                    .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                    .averageRating(averageRating)
                    .reviewCount(reviewCount)
                    .isActive(product.getIsActive())
                    .createdAt(product.getCreatedAt())
                    .build();
        });
    }


    /**
     * Get category with ALL descendants (recursive)
     * Used for expandable trees, showing entire category branch
     */
    @Transactional(readOnly = true)
    public CategoryDTO getCategoryWithAllDescendants(String slug) {
        Category category = categoryRepository.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));

        return mapToCategoryDTORecursive(category);
    }

    private CategoryDTO mapToCategoryDTORecursive(Category category) {
        CategoryDTO dto = mapToCategoryDTOWithCount(category);

        List<Category> children = categoryRepository
                .findByParentIdAndIsActiveTrueOrderByDisplayOrderAsc(category.getId());

        List<CategoryDTO> childDTOs = children.stream()
                .map(this::mapToCategoryDTORecursive)
                .collect(Collectors.toList());

        dto.setSubCategories(childDTOs);
        return dto;
    }

    /**
     * Build full category path string
     */
    private String buildCategoryPath(Category category) {
        List<String> pathParts = new ArrayList<>();
        Category current = category;

        while (current != null) {
            pathParts.add(0, current.getName());
            current = current.getParent();
        }

        return String.join(" / ", pathParts);
    }

    private CategoryDTO mapToCategoryDTO(Category category) {
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

    /**
     * Map Category entity to DTO with product count
     */
    private CategoryDTO mapToCategoryDTOWithCount(Category category) {
        Long productCount = categoryRepository.countProductsByCategoryId(category.getId());

        CategoryDTO dto = mapToCategoryDTO(category);
        dto.setProductCount(productCount);

        return dto;
    }
}
