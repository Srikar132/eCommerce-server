package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.model.entity.ProductVariant;
import com.nala.armoire.repository.CategoryRepository;
import com.nala.armoire.repository.BrandRepository;
import com.nala.armoire.repository.ProductRepository;
import com.nala.armoire.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Admin Product Management Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductSyncService productSyncService;
    private final EmailService emailService;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${inventory.low-stock-threshold:10}")
    private int lowStockThreshold;

    /**
     * Get all products for admin (includes inactive)
     */
    @Transactional(readOnly = true)
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        return products.map(this::mapToDTO);
    }

    /**
     * Create new product (can be draft or published)
     */
    @Transactional
    public ProductDTO createProduct(Product product) {
        // Validate category if provided
        if (product.getCategory() != null) {
            categoryRepository.findById(product.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }

        // Validate brand if provided
        if (product.getBrand() != null) {
            brandRepository.findById(product.getBrand().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
        }

        // Set default values if not provided
        if (product.getIsDraft() == null) {
            product.setIsDraft(true); // Default to draft for new products
        }
        
        if (product.getIsActive() == null) {
            product.setIsActive(false); // Inactive by default for drafts
        }

        // Save product
        Product savedProduct = productRepository.save(product);

        // Sync to Elasticsearch only if not a draft
        if (!savedProduct.getIsDraft()) {
            productSyncService.syncProduct(savedProduct.getId());
        }

        log.info("Admin: Created product: {} (draft: {})", savedProduct.getName(), savedProduct.getIsDraft());

        return mapToDTO(savedProduct);
    }

    /**
     * Update existing product (supports draft workflow with debounce saves)
     */
    @Transactional
    public ProductDTO updateProduct(UUID productId, Product productUpdate) {
        Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Update basic fields (always allowed)
        if (productUpdate.getName() != null) {
            existingProduct.setName(productUpdate.getName());
        }
        if (productUpdate.getDescription() != null) {
            existingProduct.setDescription(productUpdate.getDescription());
        }
        if (productUpdate.getBasePrice() != null) {
            existingProduct.setBasePrice(productUpdate.getBasePrice());
        }
        if (productUpdate.getIsCustomizable() != null) {
            existingProduct.setIsCustomizable(productUpdate.getIsCustomizable());
        }
        if (productUpdate.getMaterial() != null) {
            existingProduct.setMaterial(productUpdate.getMaterial());
        }
        if (productUpdate.getCareInstructions() != null) {
            existingProduct.setCareInstructions(productUpdate.getCareInstructions());
        }
        if (productUpdate.getSku() != null) {
            existingProduct.setSku(productUpdate.getSku());
        }

        // Update draft status
        if (productUpdate.getIsDraft() != null) {
            existingProduct.setIsDraft(productUpdate.getIsDraft());
        }

        // Update active status
        if (productUpdate.getIsActive() != null) {
            existingProduct.setIsActive(productUpdate.getIsActive());
        }

        // Validate and update category
        if (productUpdate.getCategory() != null) {
            categoryRepository.findById(productUpdate.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            existingProduct.setCategory(productUpdate.getCategory());
        }

        // Validate and update brand
        if (productUpdate.getBrand() != null) {
            brandRepository.findById(productUpdate.getBrand().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
            existingProduct.setBrand(productUpdate.getBrand());
        }

        Product savedProduct = productRepository.save(existingProduct);

        // Sync to Elasticsearch only if not a draft
        if (!savedProduct.getIsDraft()) {
            productSyncService.syncProduct(savedProduct.getId());
        } else {
            // If product became draft, remove from Elasticsearch
            try {
                productSyncService.deleteProduct(savedProduct.getId());
            } catch (Exception e) {
                log.warn("Failed to remove draft product from Elasticsearch: {}", e.getMessage());
            }
        }

        log.info("Admin: Updated product: {} (draft: {})", savedProduct.getId(), savedProduct.getIsDraft());

        return mapToDTO(savedProduct);
    }

    /**
     * Toggle product active status
     */
    @Transactional
    public ProductDTO toggleProductStatus(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setIsActive(!product.getIsActive());
        productRepository.save(product);

        // Sync to Elasticsearch only if not a draft
        if (!product.getIsDraft()) {
            productSyncService.syncProduct(product.getId());
        }

        log.info("Admin: Toggled product status: {}, active: {}, draft: {}",
                product.getId(), product.getIsActive(), product.getIsDraft());

        return mapToDTO(product);
    }

    /**
     * Delete product
     */
    @Transactional
    public void deleteProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Delete from Elasticsearch first
        productSyncService.deleteProduct(productId);

        // Delete from database
        productRepository.delete(product);

        log.info("Admin: Deleted product: {}", productId);
    }

    /**
     * Get low stock products
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLowStockProducts() {
        List<ProductVariant> allVariants = productVariantRepository.findAll();

        List<Map<String, Object>> lowStockItems = new ArrayList<>();

        for (ProductVariant variant : allVariants) {
            if (variant.getStockQuantity() <= lowStockThreshold && variant.getIsActive()) {
                Map<String, Object> item = new HashMap<>();
                item.put("variantId", variant.getId());
                item.put("productId", variant.getProduct().getId());
                item.put("productName", variant.getProduct().getName());
                item.put("size", variant.getSize());
                item.put("color", variant.getColor());
                item.put("currentStock", variant.getStockQuantity());
                item.put("threshold", lowStockThreshold);

                lowStockItems.add(item);
            }
        }

        log.info("Admin: Found {} low stock items", lowStockItems.size());

        return lowStockItems;
    }

    /**
     * Update variant stock
     */
    @Transactional
    public void updateVariantStock(UUID variantId, Integer newStock) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        int oldStock = variant.getStockQuantity();
        variant.setStockQuantity(newStock);

        // Auto-disable if out of stock
        if (newStock <= 0) {
            variant.setIsActive(false);
        } else if (oldStock <= 0 && newStock > 0) {
            // Re-enable if restocked
            variant.setIsActive(true);
        }

        productVariantRepository.save(variant);

        // Sync product to Elasticsearch
        productSyncService.syncProduct(variant.getProduct().getId());

        log.info("Admin: Updated variant stock: {}, old: {}, new: {}",
                variantId, oldStock, newStock);

        // Send low stock alert if below threshold
        if (newStock <= lowStockThreshold && newStock > 0) {
            try {
                emailService.sendLowStockAlertEmail(adminEmail, variant);
            } catch (Exception e) {
                log.error("Failed to send low stock alert", e);
            }
        }
    }

    /**
     * Get product statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProductStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalProducts = productRepository.count();
        long activeProducts = productRepository.findAll().stream()
                .filter(Product::getIsActive)
                .count();

        stats.put("totalProducts", totalProducts);
        stats.put("activeProducts", activeProducts);
        stats.put("inactiveProducts", totalProducts - activeProducts);
        stats.put("lowStockCount", getLowStockProducts().size());

        return stats;
    }

    /**
     * Publish a draft product
     */
    @Transactional
    public ProductDTO publishProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!product.getIsDraft()) {
            throw new IllegalStateException("Product is already published");
        }

        product.setIsDraft(false);
        product.setIsActive(true); // Activate when publishing
        productRepository.save(product);

        // Sync to Elasticsearch
        productSyncService.syncProduct(product.getId());

        log.info("Admin: Published product: {}", product.getId());

        return mapToDTO(product);
    }

    /**
     * Convert a published product back to draft
     */
    @Transactional
    public ProductDTO unpublishProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getIsDraft()) {
            throw new IllegalStateException("Product is already a draft");
        }

        product.setIsDraft(true);
        product.setIsActive(false); // Deactivate when unpublishing
        productRepository.save(product);

        // Remove from Elasticsearch
        productSyncService.deleteProduct(product.getId());

        log.info("Admin: Unpublished product: {}", product.getId());

        return mapToDTO(product);
    }

    private ProductDTO mapToDTO(Product product) {
        // Use existing mapping from ProductService
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .sku(product.getSku())
                .isCustomizable(product.getIsCustomizable())
                .material(product.getMaterial())
                .careInstructions(product.getCareInstructions())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .isActive(product.getIsActive())
                .isDraft(product.getIsDraft())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}