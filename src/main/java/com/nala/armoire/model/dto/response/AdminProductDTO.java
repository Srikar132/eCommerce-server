package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin Product DTO - Extended version with admin-specific fields
 * Includes all public fields plus stock info, order counts, and internal metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProductDTO {
    
    // ===== Basic Product Info (Same as ProductDTO) =====
    private UUID id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private String sku;
    
    @JsonProperty("isCustomizable")
    private Boolean isCustomizable;
    
    private String material;
    private String careInstructions;
    
    // ===== Category & Brand =====
    private UUID categoryId;
    private String categoryName;
    private UUID brandId;
    private String brandName;
    
    // ===== Images =====
    private String imageUrl; // Primary image from first active variant
    
    // ===== Ratings & Reviews =====
    private Double averageRating;
    private Long reviewCount;
    
    // ===== Status Flags =====
    @JsonProperty("isActive")
    private Boolean isActive;
    
    @JsonProperty("isDraft")
    private Boolean isDraft;
    
    // ===== Timestamps =====
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // ===== ADMIN-ONLY FIELDS =====
    
    /**
     * Total stock across all variants
     */
    private Integer totalStock;
    
    /**
     * Total number of orders containing this product
     */
    private Long totalOrders;
    
    /**
     * Stock status indicator
     * Values: IN_STOCK, LOW_STOCK, OUT_OF_STOCK
     */
    private String stockStatus;
    
    /**
     * Created by admin user ID
     */
    private UUID createdBy;
    
    /**
     * Last updated by admin user ID
     */
    private UUID updatedBy;
}
