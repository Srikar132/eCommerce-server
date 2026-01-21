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
public class ProductCreateRequest {
    
    @NotBlank(message = "Product name is required")
    @Size(max = 255)
    private String name;
    
    @NotBlank(message = "Slug is required")
    @Size(max = 255)
    private String slug;
    
    @Size(max = 5000)
    private String description;
    
    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal basePrice;
    
    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;
    
    @Builder.Default
    private Boolean isCustomizable = true;
    
    @Size(max = 100)
    private String material;
    
    private String careInstructions;
    
    private UUID categoryId;
    private UUID brandId;
    
    @Builder.Default
    private Boolean isDraft = true;
    @Builder.Default
    private Boolean isActive = false;
    
    // NEW: Variants with images
    @Valid
    @Builder.Default
    private List<VariantCreateRequest> variants = new ArrayList<>();
}
