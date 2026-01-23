package com.nala.armoire.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToCartRequest {

    @NotNull(message = "Product id is required")
    private UUID productId;

    private UUID productVariantId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 100, message = "Quantity cannot exceed 100")
    private Integer quantity;
    
    private String additionalNotes;
    
    /**
     * Optional: Inline customization data
     * If provided, a new customization will be created and attached to cart item
     */
    private CustomizationData customizationData;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomizationData {
        @NotNull(message = "Design ID is required for customization")
        private UUID designId;
        
        @NotNull(message = "Thread color is required for customization")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Thread color must be in hex format (#RRGGBB)")
        private String threadColorHex;
        
        private String additionalNotes;
    }
}
