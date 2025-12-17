package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CartItemResponse {
    private UUID id;
    private ProductSummary product;
    private VariantSummary variant;
    private CustomizationSummary customization;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal customizationPrice;
    private BigDecimal itemTotal;
    private String customizationSummary;
    private LocalDateTime addedAt;
}
