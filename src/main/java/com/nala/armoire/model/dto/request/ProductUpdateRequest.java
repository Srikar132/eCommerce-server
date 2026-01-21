package com.nala.armoire.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductUpdateRequest {
    
    @Size(max = 255)
    private String name;
    
    @Size(max = 255)
    private String slug;
    
    @Size(max = 5000)
    private String description;
    
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal basePrice;
    
    @Size(max = 100)
    private String sku;
    
    private Boolean isCustomizable;
    
    @Size(max = 100)
    private String material;
    
    private String careInstructions;
    
    private UUID categoryId;
    private UUID brandId;
    
    private Boolean isDraft;
    private Boolean isActive;
    
    // Optional: Variants with images for update
    @Valid
    @Builder.Default
    private List<VariantCreateRequest> variants = new ArrayList<>();
}
