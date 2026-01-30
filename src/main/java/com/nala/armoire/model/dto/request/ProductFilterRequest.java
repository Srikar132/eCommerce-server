package com.nala.armoire.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simplified DTO for filtering products in admin panel
 * All filters are optional and can be combined
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterRequest {

    // Text search (searches in name, description, SKU)
    private String search;

    // Category filter
    private UUID categoryId;

    // Brand filter
    private UUID brandId;

    // Status filters
    private Boolean isActive;
    private Boolean isDraft;
    private Boolean isCustomizable;

    // Price range filters
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    // Rating filter
    private Double minRating;

    // Stock status filter
    // Values: "LOW_STOCK", "IN_STOCK", "OUT_OF_STOCK"
    private String stockStatus;

}
