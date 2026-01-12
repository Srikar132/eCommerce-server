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
    private UUID userId;
    private String sessionId;
    private UUID productId;
    private UUID variantId;
    private UUID designId;
    private String threadColorHex;
    private String previewImageUrl;
    private Boolean isCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
