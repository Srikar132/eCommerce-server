package com.nala.armoire.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        
        @NotNull(message = "Quantity is required")
        private Integer quantity;
        
        // Local customization data (if this is a customized item)
        private LocalCustomizationData customizationData;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocalCustomizationData {
        @NotNull(message = "Design ID is required for customization")
        private java.util.UUID designId;
        
        @NotNull(message = "Thread color is required for customization")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Thread color must be in hex format (#RRGGBB)")
        private String threadColorHex;
        
        private String additionalNotes;
    }
}

