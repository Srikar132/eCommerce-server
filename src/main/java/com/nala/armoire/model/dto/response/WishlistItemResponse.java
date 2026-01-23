package com.nala.armoire.model.dto.response;

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
public class WishlistItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productSlug;
    private BigDecimal basePrice;
    private String sku;
    private Boolean isActive;
    private Boolean isCustomizable;
    private String primaryImageUrl;
    private String categoryName;
    private String brandName;
    private LocalDateTime addedAt;
    private Boolean inStock;
}
