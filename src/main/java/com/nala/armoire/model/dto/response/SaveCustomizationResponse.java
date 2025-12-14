package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveCustomizationResponse {
    private String customizationId;
    private String previewUrl;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
}
