package com.nala.armoire.service;

import com.nala.armoire.model.entity.Product;
import com.nala.armoire.model.entity.ProductImage;
import com.nala.armoire.model.entity.ProductVariant;
import com.nala.armoire.model.entity.VariantImage;
import com.nala.armoire.repository.ProductImageRepository;
import com.nala.armoire.repository.VariantImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing product images and variant-image relationships
 * Handles the many-to-many relationship through VariantImage junction table
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final VariantImageRepository variantImageRepository;

    /**
     * Batch fetch images for multiple variants (avoids N+1)
     * Returns map of variantId -> List<ProductImage>
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<ProductImage>> getImagesForVariants(List<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<VariantImage> variantImages = variantImageRepository.findByVariantIdsWithImages(variantIds);

        return variantImages.stream()
                .collect(Collectors.groupingBy(
                        vi -> vi.getProductVariant().getId(),
                        Collectors.mapping(
                                VariantImage::getProductImage,
                                Collectors.toList()
                        )
                ));
    }

    /**
     * Get images for a single variant
     */
    @Transactional(readOnly = true)
    public List<ProductImage> getImagesForVariant(UUID variantId) {
        return variantImageRepository.findImagesByVariantId(variantId);
    }

    /**
     * Assign an existing image to a variant
     */
    @Transactional
    public void assignImageToVariant(ProductImage image, ProductVariant variant, Integer displayOrder) {
        // Check if relationship already exists
        VariantImage existing = variantImageRepository.findByVariantIdAndImageId(
                variant.getId(), image.getId());

        if (existing == null) {
            VariantImage variantImage = VariantImage.builder()
                    .productImage(image)
                    .productVariant(variant)
                    .displayOrder(displayOrder != null ? displayOrder : 0)
                    .build();
            variantImageRepository.save(variantImage);
            log.debug("Assigned image {} to variant {}", image.getId(), variant.getId());
        } else {
            log.debug("Image {} already assigned to variant {}", image.getId(), variant.getId());
        }
    }

    /**
     * Create and assign a new image to variant(s)
     */
    @Transactional
    public ProductImage createAndAssignImage(Product product, List<ProductVariant> variants,
                                            String imageUrl, String altText, Integer displayOrder,
                                            Boolean isPrimary, String imageType) {
        // Create the image at product level
        ProductImage image = ProductImage.builder()
                .product(product)
                .imageUrl(imageUrl)
                .altText(altText)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .isPrimary(isPrimary != null ? isPrimary : false)
                .build();

        // Save the image
        image = productImageRepository.save(image);

        // Assign to variants
        for (ProductVariant variant : variants) {
            assignImageToVariant(image, variant, displayOrder);
        }

        log.info("Created and assigned image {} to {} variants", image.getId(), variants.size());
        return image;
    }

    /**
     * Remove image from a variant (but keep the image if used by other variants)
     */
    @Transactional
    public void removeImageFromVariant(UUID imageId, UUID variantId) {
        VariantImage variantImage = variantImageRepository.findByVariantIdAndImageId(variantId, imageId);
        if (variantImage != null) {
            variantImageRepository.delete(variantImage);
            log.debug("Removed image {} from variant {}", imageId, variantId);
        }
    }

    /**
     * Delete image completely if not used by any variant
     * Returns true if deleted, false if still in use
     */
    @Transactional
    public boolean deleteImageIfUnused(UUID imageId) {
        boolean isUsed = variantImageRepository.isImageUsedByAnyVariant(imageId);
        if (!isUsed) {
            productImageRepository.deleteById(imageId);
            log.info("Deleted unused image {}", imageId);
            return true;
        }
        log.debug("Image {} still in use, not deleted", imageId);
        return false;
    }

    /**
     * Get all images for a product (regardless of variant assignment)
     */
    @Transactional(readOnly = true)
    public List<ProductImage> getProductImages(UUID productId) {
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
    }

    /**
     * Get primary image for a product
     */
    @Transactional(readOnly = true)
    public ProductImage getPrimaryImage(UUID productId) {
        return productImageRepository.findPrimaryImageByProductId(productId);
    }

    /**
     * Batch fetch images for multiple products
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<ProductImage>> getImagesForProducts(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<ProductImage> images = productImageRepository.findByProductIds(productIds);

        return images.stream()
                .collect(Collectors.groupingBy(
                        img -> img.getProduct().getId(),
                        Collectors.toList()
                ));
    }
}
