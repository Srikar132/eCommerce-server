package com.nala.armoire.service;

import com.nala.armoire.model.document.ProductDocument;
import com.nala.armoire.model.entity.*;
import com.nala.armoire.repository.ProductElasticsearchRepository;
import com.nala.armoire.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSyncService {

    private final ProductRepository productRepository;
    private final ProductElasticsearchRepository productElasticsearchRepository;

    /**
     * Sync a single product to Elasticsearch
     */
    @Async
    public void syncProduct(UUID productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

            ProductDocument document = convertToDocument(product);
            productElasticsearchRepository.save(document);

            log.info("Synced product to Elasticsearch: {}", productId);
        } catch (Exception e) {
            log.error("Failed to sync product to Elasticsearch: {}", productId, e);
        }
    }

    /**
     * Sync all products to Elasticsearch (bulk operation)
     */
    @Transactional(readOnly = true)
    public void syncAllProducts() {
        log.info("Starting bulk sync of all products to Elasticsearch");

        try {
            List<Product> products = productRepository.findAll();

            List<ProductDocument> documents = products.stream()
                    .map(this::convertToDocument)
                    .collect(Collectors.toList());

            productElasticsearchRepository.saveAll(documents);

            log.info("Successfully synced {} products to Elasticsearch", documents.size());
        } catch (Exception e) {
            log.error("Failed to sync products to Elasticsearch", e);
            throw new RuntimeException("Bulk sync failed", e);
        }
    }

    /**
     * Delete product from Elasticsearch
     */
    @Async
    public void deleteProduct(UUID productId) {
        try {
            productElasticsearchRepository.deleteById(productId.toString());
            log.info("Deleted product from Elasticsearch: {}", productId);
        } catch (Exception e) {
            log.error("Failed to delete product from Elasticsearch: {}", productId, e);
        }
    }

    /**
     * Convert JPA Product entity to Elasticsearch ProductDocument
     */
    private ProductDocument convertToDocument(Product product) {
        // Extract available sizes and colors from variants
        List<String> availableSizes = product.getVariants().stream()
                .filter(v -> v.getIsActive() && v.getStockQuantity() > 0)
                .map(ProductVariant::getSize)
                .distinct()
                .collect(Collectors.toList());

        List<String> availableColors = product.getVariants().stream()
                .filter(v -> v.getIsActive() && v.getStockQuantity() > 0)
                .map(v -> v.getColor().toLowerCase())
                .distinct()
                .collect(Collectors.toList());

        // Convert variants
        List<ProductDocument.VariantInfo> variantInfos = product.getVariants().stream()
                .map(v -> ProductDocument.VariantInfo.builder()
                        .id(v.getId().toString())
                        .size(v.getSize())
                        .color(v.getColor())
                        .colorHex(v.getColorHex())
                        .stockQuantity(v.getStockQuantity())
                        .additionalPrice(v.getAdditionalPrice())
                        .isActive(v.getIsActive())
                        .build())
                .collect(Collectors.toList());

        // Convert images
        List<ProductDocument.ImageInfo> imageInfos = product.getImages().stream()
                .map(img -> ProductDocument.ImageInfo.builder()
                        .id(img.getId().toString())
                        .imageUrl(img.getImageUrl())
                        .altText(img.getAltText())
                        .displayOrder(img.getDisplayOrder())
                        .isPrimary(img.getIsPrimary())
                        .build())
                .collect(Collectors.toList());

        // Get primary image
        String primaryImageUrl = product.getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);

        // Calculate average rating
        Double averageRating = product.getReviews().isEmpty() ? null :
                product.getReviews().stream()
                        .mapToInt(Review::getRating)
                        .average()
                        .orElse(0.0);

        return ProductDocument.builder()
                .id(product.getId().toString())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .sku(product.getSku())
                .isCustomizable(product.getIsCustomizable())
                .material(product.getMaterial())
                .careInstructions(product.getCareInstructions())
                .categorySlug(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId().toString() : null)
                .brandSlug(product.getBrand() != null ? product.getBrand().getSlug() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId().toString() : null)
                .variants(variantInfos)
                .availableSizes(availableSizes)
                .availableColors(availableColors)
                .images(imageInfos)
                .primaryImageUrl(primaryImageUrl)
                .averageRating(averageRating)
                .reviewCount((long) product.getReviews().size())
                .isActive(product.getIsActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}