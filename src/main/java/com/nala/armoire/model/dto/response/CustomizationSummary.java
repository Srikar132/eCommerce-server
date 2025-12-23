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
public class CustomizationSummary {
    private UUID id;
    private String customizationId;
    private String previewImageUrl;
    private String thumbnailUrl;
    private Boolean hasText;
    private Boolean hasDesign;
    private Boolean hasUploadedImage;
}
