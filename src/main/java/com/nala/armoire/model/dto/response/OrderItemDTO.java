package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productSlug;
    private UUID variantId;
    private String size;
    private String color;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private Boolean hasCustomization;
    private String customizationSnapshot;
    private String productionStatus;
    private String imageUrl;
}