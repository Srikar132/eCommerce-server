package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * VariantDTO - Data Transfer Object for ProductVariant
 * Used to safely serialize variant data without lazy loading issues
 * JsonInclude.NON_NULL ensures null fields are not included in JSON response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VariantDTO {
    private UUID id;
    private UUID productId;
    private String size;
    private String color;
    private String colorHex;
    private Integer stockQuantity;
    private BigDecimal additionalPrice;
    private String sku;
    private Boolean isActive;
    private List<ImageDTO> images;

    // Optional timestamp fields (if your entity has them)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}