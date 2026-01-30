package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.BrandDTO;
import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.model.entity.Brand;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.repository.BrandRepository;
import com.nala.armoire.repository.ProductRepository;
import com.nala.armoire.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public List<BrandDTO> getAllBrands(Boolean includeActive) {
        List<Brand> brands;

        if(includeActive) {
            brands = brandRepository.findAll();
        } else {
            brands = brandRepository.findByIsActiveTrueOrderByNameAsc();
        }

        return brands.stream()
                .map(this::mapToBrandDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> getProductsByBrand(String brandSlug, Pageable pageable) {

        Brand brand = brandRepository.findBySlugAndIsActiveTrue(brandSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Brand Not Found"));

        Page<Product> products = productRepository.findByBrandIdAndIsActiveTrueAndIsDraftFalse(brand.getId(), pageable);

        // OPTIMIZED: Batch fetch review stats to avoid N+1 queries
        if (products.isEmpty()) {
            return Page.empty();
        }
        
        List<UUID> productIds = products.getContent().stream()
                .map(Product::getId)
                .collect(Collectors.toList());
        
        // Batch query: Get average ratings for all products (1 query instead of N)
        Map<UUID, Double> avgRatingMap = reviewRepository.findAverageRatingsByProductIds(productIds)
                .stream()
                .collect(Collectors.toMap(
                    arr -> (UUID) arr[0],
                    arr -> (Double) arr[1]
                ));
        
        // Batch query: Get review counts for all products (1 query instead of N)
        Map<UUID, Long> reviewCountMap = reviewRepository.countReviewsByProductIds(productIds)
                .stream()
                .collect(Collectors.toMap(
                    arr -> (UUID) arr[0],
                    arr -> (Long) arr[1]
                ));

        return products.map(product -> {
            Double averageRating = avgRatingMap.get(product.getId());
            Long reviewCount = reviewCountMap.getOrDefault(product.getId(), 0L);

            return ProductDTO.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .slug(product.getSlug())
                    .description(product.getDescription())
                    .basePrice(product.getBasePrice())
                    .sku(product.getSku())
                    .isCustomizable(product.getIsCustomizable())
                    .material(product.getMaterial())
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .brandId(product.getBrand().getId())
                    .brandName(product.getBrand().getName())
                    .averageRating(averageRating)
                    .reviewCount(reviewCount)
                    .isActive(product.getIsActive())
                    .isDraft(product.getIsDraft())
                    .createdAt(product.getCreatedAt())
                    .build();
        });
    }

    private BrandDTO mapToBrandDTO(Brand brand) {
        return BrandDTO.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .description(brand.getDescription())
                .logoUrl(brand.getLogoUrl())
                .isActive(brand.getIsActive())
                .createdAt(brand.getCreatedAt())
                .build();
    }
}
