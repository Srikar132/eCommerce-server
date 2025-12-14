package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomizationDTO {
    private String customizationId;
    private UUID productId;
    private String previewImageUrl;
    private String thumbnailUrl;
    private CustomizationConfigDTO configuration;
    private Boolean hasText;
    private Boolean hasDesign;
    private Boolean hasUploadedImage;
    private Integer layerCount;
    private Boolean isCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
