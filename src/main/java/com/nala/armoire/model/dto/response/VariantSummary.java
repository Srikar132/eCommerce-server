package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariantSummary {
    private UUID id;
    private String size;
    private String color;
    private String sku;
    private String primaryImageUrl;  // The main image for this variant
}
