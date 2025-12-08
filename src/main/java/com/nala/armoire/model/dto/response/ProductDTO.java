package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private Boolean isCustomizable;
    private String material;
    private String careInstructions;
    private UUID categoryId;
    private String categoryName;
    private UUID brandId;
    private String brandName;
    private List<ProductImageDTO> images;
    private Double averageRating;
    private Long reviewCount;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
