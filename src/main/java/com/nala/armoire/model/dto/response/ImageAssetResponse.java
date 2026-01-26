package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Image Asset Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageAssetResponse {
    private UUID id;
    private String fileName;
    private String imageUrl;
    private Long fileSize;
    private String mimeType;
    private String dimensions;
    private LocalDateTime createdAt;
}
