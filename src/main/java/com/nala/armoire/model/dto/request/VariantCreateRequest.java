package com.nala.armoire.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariantCreateRequest {
    
    @NotBlank(message = "Size is required")
    private String size;
    
    @NotBlank(message = "Color is required")
    private String color;
    
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Invalid hex color")
    private String colorHex;
    
    @NotNull(message = "Stock quantity is required")
    @Min(0)
    private Integer stockQuantity;
    
    @NotNull
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal additionalPrice = BigDecimal.ZERO;
    
    @NotBlank(message = "Variant SKU is required")
    private String sku;
    
    @Builder.Default
    private Boolean isActive = true;
    
    // NEW: Images for this variant
    @Valid
    @Builder.Default
    private List<ImageCreateRequest> images = new ArrayList<>();
}
