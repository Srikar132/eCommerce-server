package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
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
    private UUID categoryId;
    private String categoryName;
    private UUID brandId;
    private String brandName;
    private String imageUrl; // Primary image from first active variant
    private Double averageRating;
    private Long reviewCount;
    
    @JsonProperty("isActive")
    private Boolean isActive;
    
    @JsonProperty("isDraft")
    private Boolean isDraft;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}