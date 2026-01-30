package com.nala.armoire.service;

import com.nala.armoire.exception.ResourceNotFoundException;
import com.nala.armoire.model.dto.request.ImageCreateRequest;
import com.nala.armoire.model.dto.request.ProductCreateRequest;
import com.nala.armoire.model.dto.request.ProductFilterRequest;
import com.nala.armoire.model.dto.request.ProductUpdateRequest;
import com.nala.armoire.model.dto.request.VariantCreateRequest;
import com.nala.armoire.model.dto.response.AdminProductDTO;
import com.nala.armoire.model.dto.response.ImageDTO;
import com.nala.armoire.model.dto.response.ProductDTO;
import com.nala.armoire.model.dto.response.VariantDTO;
import com.nala.armoire.model.entity.Brand;
import com.nala.armoire.model.entity.Category;
import com.nala.armoire.model.entity.ImageRole;
import com.nala.armoire.model.entity.Product;
import com.nala.armoire.model.entity.ProductImage;
import com.nala.armoire.model.entity.ProductVariant;
import com.nala.armoire.repository.CategoryRepository;
import com.nala.armoire.repository.BrandRepository;
import com.nala.armoire.repository.OrderItemRepository;
import com.nala.armoire.repository.ProductRepository;
import com.nala.armoire.repository.ProductVariantRepository;
import com.nala.armoire.repository.ReviewRepository;
import com.nala.armoire.specification.AdminProductSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

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
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final EmailService emailService;
    private final AdminProductSpecification productSpecification;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${inventory.low-stock-threshold:10}")
    private int lowStockThreshold;


    /**
     * Get filtered products for admin with dynamic filters
     * OPTIMIZED: Uses batch queries to avoid N+1 problem
     * 
     * @param filterRequest Filter criteria including stockStatus
     * @param pageable Pagination settings (sorting applied to database fields only)
     * @param sortBy Sort field name (for calculated fields like totalStock, averageRating, totalOrders)
     * @return Page of AdminProductDTO
     */
    @Transactional(readOnly = true)
    public Page<AdminProductDTO> getFilteredProducts(
            ProductFilterRequest filterRequest, 
            @NonNull Pageable pageable,
            String sortBy) {
        
        log.info("Admin: Fetching filtered products with filters: {}, sortBy: {}", filterRequest, sortBy);
        
        Specification<Product> spec = productSpecification.buildSpecification(filterRequest);
        
        // For calculated fields, we need to fetch all matching products first
        // Then sort in memory (not ideal but necessary for calculated fields)
        boolean isCalculatedFieldSort = sortBy != null && 
            (sortBy.equals("totalStock") || sortBy.equals("averageRating") || 
             sortBy.equals("totalOrders") || sortBy.equals("reviewCount"));
        
        Page<Product> products;
        
        if (isCalculatedFieldSort) {
            // Fetch without sorting, we'll sort after mapping
            log.info("Admin: Sorting by calculated field '{}' - will sort in memory", sortBy);
            Pageable unsortedPageable = PageRequest.of(
                pageable.getPageNumber(), 
                pageable.getPageSize()
            );
            products = productRepository.findAll(spec, unsortedPageable);
        } else {
            // Normal database sorting
            products = productRepository.findAll(spec, pageable);
        }
        
        log.info("Admin: Found {} products matching filters", products.getTotalElements());
        
        // Map to DTOs with batch-loaded data
        Page<AdminProductDTO> dtoPage = mapToAdminDTOPage(products, filterRequest);
        
        // Apply in-memory sorting for calculated fields
        if (isCalculatedFieldSort) {
            dtoPage = sortByCalculatedField(dtoPage, sortBy, pageable.getSort());
        }
        
        return dtoPage;
    }

    /**
     * Sort AdminProductDTO page by calculated fields in memory
     */
    private Page<AdminProductDTO> sortByCalculatedField(
            Page<AdminProductDTO> page, 
            String sortBy, 
            Sort sort) {
        
        // Create a mutable copy of the list (page.getContent() returns unmodifiable list)
        List<AdminProductDTO> content = new ArrayList<>(page.getContent());
        Sort.Direction direction = sort.isSorted() 
            ? sort.iterator().next().getDirection() 
            : Sort.Direction.DESC;
        
        switch (sortBy) {
            case "totalStock":
                content.sort((a, b) -> {
                    Integer stockA = a.getTotalStock() != null ? a.getTotalStock() : 0;
                    Integer stockB = b.getTotalStock() != null ? b.getTotalStock() : 0;
                    return direction.isAscending()
                        ? Integer.compare(stockA, stockB)
                        : Integer.compare(stockB, stockA);
                });
                break;
            case "averageRating":
                content.sort((a, b) -> {
                    Double ratingA = a.getAverageRating() != null ? a.getAverageRating() : 0.0;
                    Double ratingB = b.getAverageRating() != null ? b.getAverageRating() : 0.0;
                    return direction.isAscending()
                        ? Double.compare(ratingA, ratingB)
                        : Double.compare(ratingB, ratingA);
                });
                break;
            case "totalOrders":
                content.sort((a, b) -> {
                    Long ordersA = a.getTotalOrders() != null ? a.getTotalOrders() : 0L;
                    Long ordersB = b.getTotalOrders() != null ? b.getTotalOrders() : 0L;
                    return direction.isAscending()
                        ? Long.compare(ordersA, ordersB)
                        : Long.compare(ordersB, ordersA);
                });
                break;
            case "reviewCount":
                content.sort((a, b) -> {
                    Long reviewsA = a.getReviewCount() != null ? a.getReviewCount() : 0L;
                    Long reviewsB = b.getReviewCount() != null ? b.getReviewCount() : 0L;
                    return direction.isAscending()
                        ? Long.compare(reviewsA, reviewsB)
                        : Long.compare(reviewsB, reviewsA);
                });
                break;
        }
        
        return new org.springframework.data.domain.PageImpl<>(
            content, 
            page.getPageable(), 
            page.getTotalElements()
        );
    }

    /**
     * Map a page of products to AdminProductDTO using batch queries
     * SIMPLE APPROACH: Let Hibernate lazy-load collections within transaction
     * 
     * @param products Page of Product entities
     * @param filterRequest Filter request (for stockStatus filtering)
     * @return Page of AdminProductDTO with calculated fields
     */
    private Page<AdminProductDTO> mapToAdminDTOPage(
            Page<Product> products, 
            ProductFilterRequest filterRequest) {
        
        if (products.isEmpty()) {
            return Page.empty();
        }

        // Extract all product IDs
        List<UUID> productIds = products.getContent().stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        // Batch fetch all statistics in 3 queries instead of N queries
        Map<UUID, Long> orderCountMap = batchGetOrderCounts(productIds);
        Map<UUID, Double> avgRatingMap = batchGetAverageRatings(productIds);
        Map<UUID, Long> reviewCountMap = batchGetReviewCounts(productIds);

        // Map products - let Hibernate lazy-load variants and images as needed
        // This is within @Transactional boundary, so lazy loading works fine
        List<AdminProductDTO> dtos = products.getContent().stream()
                .map(product -> {
                    // Initialize lazy collections by accessing them
                    // This triggers Hibernate to load them within the transaction
                    if (product.getVariants() != null) {
                        product.getVariants().size(); // Force load variants
                        
                        // Force load variant images
                        product.getVariants().forEach(variant -> {
                            if (variant.getVariantImages() != null) {
                                variant.getVariantImages().size();
                            }
                        });
                    }
                    
                    return mapToAdminDTO(
                            product, 
                            orderCountMap.getOrDefault(product.getId(), 0L),
                            avgRatingMap.get(product.getId()),
                            reviewCountMap.getOrDefault(product.getId(), 0L)
                    );
                })
                .collect(Collectors.toList());
        
        // Filter by stockStatus if requested
        if (filterRequest.getStockStatus() != null && !filterRequest.getStockStatus().isEmpty()) {
            log.info("Admin: Filtering by stockStatus: {}", filterRequest.getStockStatus());
            dtos = dtos.stream()
                    .filter(dto -> filterRequest.getStockStatus().equals(dto.getStockStatus()))
                    .collect(Collectors.toList());
        }
        
        // Use original total elements count from database, not filtered list size
        return new org.springframework.data.domain.PageImpl<>(
            dtos, 
            products.getPageable(), 
            products.getTotalElements()  // Preserve original pagination metadata
        );
    }

    /**
     * Batch fetch order counts for multiple products (1 query)
     */
    private Map<UUID, Long> batchGetOrderCounts(List<UUID> productIds) {
        try {
            List<Object[]> results = orderItemRepository.countOrdersByProductIds(productIds);
            return results.stream()
                    .collect(Collectors.toMap(
                            row -> (UUID) row[0],
                            row -> (Long) row[1]
                    ));
        } catch (Exception e) {
            log.warn("Error batch fetching order counts: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Batch fetch average ratings for multiple products (1 query)
     */
    private Map<UUID, Double> batchGetAverageRatings(List<UUID> productIds) {
        try {
            List<Object[]> results = reviewRepository.findAverageRatingsByProductIds(productIds);
            return results.stream()
                    .collect(Collectors.toMap(
                            row -> (UUID) row[0],
                            row -> (Double) row[1]
                    ));
        } catch (Exception e) {
            log.warn("Error batch fetching average ratings: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Batch fetch review counts for multiple products (1 query)
     */
    private Map<UUID, Long> batchGetReviewCounts(List<UUID> productIds) {
        try {
            List<Object[]> results = reviewRepository.countReviewsByProductIds(productIds);
            return results.stream()
                    .collect(Collectors.toMap(
                            row -> (UUID) row[0],
                            row -> (Long) row[1]
                    ));
        } catch (Exception e) {
            log.warn("Error batch fetching review counts: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Get product by ID
     */
    @Transactional(readOnly = true)
    public ProductDTO getProductById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return mapToDTO(product);
    }

    /**
     * Get all variants for a product by ID (includes inactive, for admin)
     * Returns DTOs to prevent lazy loading and circular reference issues
     */
    @Transactional(readOnly = true)
    public List<VariantDTO> getProductVariantsForAdmin(UUID productId) {
        // Verify product exists first
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        log.info("Admin: Fetching variants for product: {}, isDraft: {}", productId, product.getIsDraft());

        // Fetch variants with images eagerly loaded (prevents lazy loading exception)
        List<ProductVariant> variants = productVariantRepository.findByProductIdWithImages(productId);

        log.info("Admin: Found {} variants for product: {}", variants.size(), productId);

        // Convert to DTOs to avoid serialization issues
        return variants.stream()
                .map(this::mapVariantToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create product with variants and images in single transaction
     */
    @Transactional
    public ProductDTO createProductWithVariants(@NonNull ProductCreateRequest request) {
        // Validate category
        Category category = null;
        if (request.getCategoryId() != null) {
            UUID categoryId = Objects.requireNonNull(request.getCategoryId(), "Category ID must not be null");
            category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        }

        // Validate brand
        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
        }

        // Create product
        Product product = Product.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .sku(request.getSku())
                .isCustomizable(request.getIsCustomizable())
                .material(request.getMaterial())
                .careInstructions(request.getCareInstructions())
                .category(category)
                .brand(brand)
                .isDraft(request.getIsDraft())
                .isActive(request.getIsActive())
                .build();

        // Add variants with images
        for (VariantCreateRequest varReq : request.getVariants()) {
            ProductVariant variant = ProductVariant.builder()
                    .size(varReq.getSize())
                    .color(varReq.getColor())
                    .colorHex(varReq.getColorHex())
                    .stockQuantity(varReq.getStockQuantity())
                    .additionalPrice(varReq.getAdditionalPrice())
                    .sku(varReq.getSku())
                    .isActive(varReq.getIsActive())
                    .build();

            product.addVariant(variant);

            // Add images to product and link to variant
            if (varReq.getImages() != null) {
                for (ImageCreateRequest imgReq : varReq.getImages()) {
                    ProductImage image = ProductImage.builder()
                            .product(product)
                            .imageUrl(imgReq.getImageUrl())
                            .altText(imgReq.getAltText())
                            .displayOrder(imgReq.getDisplayOrder())
                            .isPrimary(imgReq.getIsPrimary())
                            .imageType(imgReq.getImageRole() != null ? imgReq.getImageRole() : ImageRole.PREVIEW_BASE)
                            .build();

                    product.addImage(image);
                    variant.addImage(image, imgReq.getDisplayOrder());
                }
            }
        }

        Product savedProduct = productRepository.save(product);

        // Sync to Elasticsearch if not draft
        if (!savedProduct.getIsDraft()) {
        }

        log.info("Created product {} with {} variants",
                savedProduct.getId(), savedProduct.getVariants().size());

        return mapToDTO(savedProduct);
    }

    /**
     * Update product with variants and images
     */
    @Transactional
    public ProductDTO updateProductWithVariants(UUID productId, ProductUpdateRequest request) {
        Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Update basic fields (only if provided)
        if (request.getName() != null) {
            existingProduct.setName(request.getName());
        }
        if (request.getSlug() != null) {
            existingProduct.setSlug(request.getSlug());
        }
        if (request.getDescription() != null) {
            existingProduct.setDescription(request.getDescription());
        }
        if (request.getBasePrice() != null) {
            existingProduct.setBasePrice(request.getBasePrice());
        }
        if (request.getSku() != null) {
            existingProduct.setSku(request.getSku());
        }
        if (request.getIsCustomizable() != null) {
            existingProduct.setIsCustomizable(request.getIsCustomizable());
        }
        if (request.getMaterial() != null) {
            existingProduct.setMaterial(request.getMaterial());
        }
        if (request.getCareInstructions() != null) {
            existingProduct.setCareInstructions(request.getCareInstructions());
        }

        // Update draft status
        if (request.getIsDraft() != null) {
            existingProduct.setIsDraft(request.getIsDraft());
        }

        // Update active status
        if (request.getIsActive() != null) {
            existingProduct.setIsActive(request.getIsActive());
        }

        // Validate and update category
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            existingProduct.setCategory(category);
        }

        // Validate and update brand
        if (request.getBrandId() != null) {
            Brand brand = brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
            existingProduct.setBrand(brand);
        }

        // Update variants if provided
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            // Clear existing variants and images
            existingProduct.getVariants().clear();
            existingProduct.getImages().clear();

            // Add new variants with images
            for (VariantCreateRequest varReq : request.getVariants()) {
                ProductVariant variant = ProductVariant.builder()
                        .size(varReq.getSize())
                        .color(varReq.getColor())
                        .colorHex(varReq.getColorHex())
                        .stockQuantity(varReq.getStockQuantity())
                        .additionalPrice(varReq.getAdditionalPrice())
                        .sku(varReq.getSku())
                        .isActive(varReq.getIsActive())
                        .build();

                existingProduct.addVariant(variant);

                // Add images to product and link to variant
                if (varReq.getImages() != null) {
                    for (ImageCreateRequest imgReq : varReq.getImages()) {
                        ProductImage image = ProductImage.builder()
                                .product(existingProduct)
                                .imageUrl(imgReq.getImageUrl())
                                .altText(imgReq.getAltText())
                                .displayOrder(imgReq.getDisplayOrder())
                                .isPrimary(imgReq.getIsPrimary())
                                .imageType(imgReq.getImageRole() != null ? imgReq.getImageRole() : ImageRole.PREVIEW_BASE)
                                .build();

                        existingProduct.addImage(image);
                        variant.addImage(image, imgReq.getDisplayOrder());
                    }
                }
            }
        }

        Product savedProduct = productRepository.save(existingProduct);

        log.info("Admin: Updated product: {} (draft: {})", savedProduct.getId(), savedProduct.getIsDraft());

        return mapToDTO(savedProduct);
    }

    /**
     * Add variant to existing product
     */
    @Transactional
    public ProductDTO addVariantToProduct(UUID productId, VariantCreateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ProductVariant variant = ProductVariant.builder()
                .size(request.getSize())
                .color(request.getColor())
                .colorHex(request.getColorHex())
                .stockQuantity(request.getStockQuantity())
                .additionalPrice(request.getAdditionalPrice())
                .sku(request.getSku())
                .isActive(request.getIsActive())
                .build();

        product.addVariant(variant);

        // Add images to product and link to variant
        if (request.getImages() != null) {
            for (ImageCreateRequest imgReq : request.getImages()) {
                ProductImage image = ProductImage.builder()
                        .product(product)
                        .imageUrl(imgReq.getImageUrl())
                        .altText(imgReq.getAltText())
                        .displayOrder(imgReq.getDisplayOrder())
                        .isPrimary(imgReq.getIsPrimary())
                        .imageType(imgReq.getImageRole() != null ? imgReq.getImageRole() : ImageRole.PREVIEW_BASE)
                        .build();

                product.addImage(image);
                variant.addImage(image, imgReq.getDisplayOrder());
            }
        }

        productRepository.save(product);

        if (!product.getIsDraft()) {
        }

        log.info("Admin: Added variant to product: {}", productId);

        return mapToDTO(product);
    }

    /**
     * Update variant including images
     */
    @Transactional
    public ProductDTO updateVariant(UUID variantId, VariantCreateRequest request) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        Product product = variant.getProduct();

        // Update variant fields
        variant.setSize(request.getSize());
        variant.setColor(request.getColor());
        variant.setColorHex(request.getColorHex());
        variant.setStockQuantity(request.getStockQuantity());
        variant.setAdditionalPrice(request.getAdditionalPrice());
        variant.setSku(request.getSku());
        variant.setIsActive(request.getIsActive());

        // Remove old variant-image links
        variant.getVariantImages().clear();

        // Remove old images from product (if not used by other variants)
        List<ProductImage> imagesToRemove = product.getImages().stream()
                .filter(img -> img.getVariantImages().isEmpty())
                .collect(Collectors.toList());
        imagesToRemove.forEach(product::removeImage);

        // Add new images
        if (request.getImages() != null) {
            for (ImageCreateRequest imgReq : request.getImages()) {
                ProductImage image = ProductImage.builder()
                        .product(product)
                        .imageUrl(imgReq.getImageUrl())
                        .altText(imgReq.getAltText())
                        .displayOrder(imgReq.getDisplayOrder())
                        .isPrimary(imgReq.getIsPrimary())
                        .imageType(imgReq.getImageRole() != null ? imgReq.getImageRole() : ImageRole.PREVIEW_BASE)
                        .build();

                product.addImage(image);
                variant.addImage(image, imgReq.getDisplayOrder());
            }
        }

        productVariantRepository.save(variant);

        if (!product.getIsDraft()) {
        }

        log.info("Admin: Updated variant: {}", variantId);

        return mapToDTO(product);
    }

    /**
     * Delete variant
     */
    @Transactional
    public void deleteVariant(UUID variantId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        Product product = variant.getProduct();
        product.removeVariant(variant);
        productRepository.save(product);

        if (!product.getIsDraft()) {
        }

        log.info("Admin: Deleted variant: {}", variantId);
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
        } else {
            // If product became draft, remove from Elasticsearch
            try {
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

        log.info("Admin: Unpublished product: {}", product.getId());

        return mapToDTO(product);
    }


    /**
     * Map ProductVariant entity to VariantDTO
     * Safely converts entity to DTO, breaking circular references
     */
    private VariantDTO mapVariantToDTO(ProductVariant variant) {
        // Map images (collection is already fetched, so this is safe)
        List<ImageDTO> images = variant.getImages().stream()
                .map(img -> ImageDTO.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .altText(img.getAltText())
                        .displayOrder(img.getDisplayOrder())
                        .isPrimary(img.getIsPrimary())
                        .imageRole(img.getImageType() != null ? img.getImageType().name() : null)
                        .build())
                .collect(Collectors.toList());

        // Build DTO
        VariantDTO.VariantDTOBuilder builder = VariantDTO.builder()
                .id(variant.getId())
                .productId(variant.getProduct().getId())
                .size(variant.getSize())
                .color(variant.getColor())
                .colorHex(variant.getColorHex())
                .stockQuantity(variant.getStockQuantity())
                .additionalPrice(variant.getAdditionalPrice())
                .sku(variant.getSku())
                .isActive(variant.getIsActive())
                .images(images);

        return builder.build();
    }

    private ProductDTO mapToDTO(Product product) {
        // Extract primary image from first active variant
        String primaryImageUrl = product.getVariants().stream()
                .filter(v -> v.getIsActive() && !v.getImages().isEmpty())
                .flatMap(v -> v.getImages().stream())
                .filter(img -> img.getIsPrimary())
                .findFirst()
                .map(img -> img.getImageUrl())
                .orElse(
                        // Fallback to first image from first active variant
                        product.getVariants().stream()
                                .filter(v -> v.getIsActive() && !v.getImages().isEmpty())
                                .flatMap(v -> v.getImages().stream())
                                .findFirst()
                                .map(img -> img.getImageUrl())
                                .orElse(null));

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
                .imageUrl(primaryImageUrl)
                .isActive(product.getIsActive())
                .isDraft(product.getIsDraft())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    /**
     * Map Product entity to AdminProductDTO with admin-specific fields
     * LEGACY: Use overloaded version with pre-fetched data for better performance
     */
    // private AdminProductDTO mapToAdminDTO(Product product) {
    //     // Calculate admin-specific fields (will cause N+1 queries)
    //     Long totalOrders = calculateTotalOrders(product);
    //     Double averageRating = calculateAverageRating(product);
    //     Long reviewCount = calculateReviewCount(product);
        
    //     return mapToAdminDTO(product, totalOrders, averageRating, reviewCount);
    // }

    /**
     * Map Product entity to AdminProductDTO with PRE-FETCHED admin data
     * OPTIMIZED: No additional queries needed
     */
    private AdminProductDTO mapToAdminDTO(Product product, Long totalOrders, Double averageRating, Long reviewCount) {
        // Extract primary image from first active variant
        String primaryImageUrl = getFirstVariantImage(product);
        
        // Calculate fields that don't require DB queries
        Integer totalStock = calculateTotalStock(product);
        String stockStatus = calculateStockStatus(totalStock);

        return AdminProductDTO.builder()
                // Basic fields from ProductDTO
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .sku(product.getSku())
                .isCustomizable(product.getIsCustomizable())
                .material(product.getMaterial())
                .careInstructions(product.getCareInstructions())
                
                // Category & Brand
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brandId(product.getBrand() != null ? product.getBrand().getId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                
                // Image
                .imageUrl(primaryImageUrl)
                
                // Ratings & Reviews (pre-fetched)
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                
                // Status
                .isActive(product.getIsActive())
                .isDraft(product.getIsDraft())
                
                // Timestamps
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                
                // ===== ADMIN-ONLY FIELDS =====
                .totalStock(totalStock)
                .totalOrders(totalOrders)
                .stockStatus(stockStatus)
                .build();
    }

    /**
     * Calculate total stock across all variants
     */
    private Integer calculateTotalStock(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return 0;
        }
        
        return product.getVariants().stream()
                .filter(variant -> variant.getStockQuantity() != null)
                .mapToInt(ProductVariant::getStockQuantity)
                .sum();
    }



    /**
     * Calculate stock status based on total stock
     */
    private String calculateStockStatus(Integer totalStock) {
        if (totalStock == null || totalStock == 0) {
            return "OUT_OF_STOCK";
        } else if (totalStock < lowStockThreshold) {
            return "LOW_STOCK";
        } else {
            return "IN_STOCK";
        }
    }

    /**
     * Get first variant image (primary or first available)
     */
    private String getFirstVariantImage(Product product) {
        return product.getVariants().stream()
                .filter(v -> v.getIsActive() && !v.getImages().isEmpty())
                .flatMap(v -> v.getImages().stream())
                .filter(ProductImage::getIsPrimary)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(
                        // Fallback to first image from first active variant
                        product.getVariants().stream()
                                .filter(v -> v.getIsActive() && !v.getImages().isEmpty())
                                .flatMap(v -> v.getImages().stream())
                                .findFirst()
                                .map(ProductImage::getImageUrl)
                                .orElse(null));
    }


}
