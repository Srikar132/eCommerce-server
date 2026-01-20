package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDTO {
    private UUID id;
    private UUID productId;
    private String size;
    private String color;
    private String colorHex;
    private Integer stockQuantity;
    private BigDecimal additionalPrice;
    private String sku;
    
    @JsonProperty("isActive")
    private Boolean isActive;
    
    private List<ProductImageDTO> images; // Images here!
}