package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Image Upload Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageUploadResponse {

    private UUID id;
    private String fileName;
    private String imageUrl; // CDN URL if available, otherwise S3 URL
    private Long fileSize;
    private String dimensions;
}
