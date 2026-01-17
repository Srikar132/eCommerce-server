package com.nala.armoire.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for syncing local cart items to backend on login/checkout
 * Used when guest user logs in and needs to merge their local cart
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLocalCartRequest {
    
    @NotNull(message = "Cart items cannot be null")
    @Valid
    private List<LocalCartItemRequest> items;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalCartItemRequest {
        @NotNull(message = "Product ID is required")
        private java.util.UUID productId;
        
        @NotNull(message = "Variant ID is required")
        private java.util.UUID productVariantId;
        
        private java.util.UUID customizationId;
        
        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        private String customizationSummary;
        
        // Local customization data (if not saved to backend yet)
        private LocalCustomizationData customizationData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalCustomizationData {
        private java.util.UUID designId;
        private java.util.UUID variantId;
        private String threadColorHex;
        private String previewImageBase64; // Base64 encoded preview image
        private String designPositionJson; // JSON string of design position/transform
    }
}
