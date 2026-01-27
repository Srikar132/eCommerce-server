package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * ImageDTO - Data Transfer Object for ProductImage
 * Used to safely serialize image data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDTO {
    private UUID id;
    private String imageUrl;
    private String altText;
    private Integer displayOrder;
    private Boolean isPrimary;
    private String imageRole; // e.g., "MAIN", "DETAIL", "LIFESTYLE"
}