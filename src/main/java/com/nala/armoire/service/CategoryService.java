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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllCategories(Boolean includeInactive) {
        List<Category> categories;

        if(includeInactive) {
            categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        } else {
            categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        }

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
}
