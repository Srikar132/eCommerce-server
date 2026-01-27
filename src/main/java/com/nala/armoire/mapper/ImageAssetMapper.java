package com.nala.armoire.mapper;

import com.nala.armoire.model.dto.response.ImageAssetResponse;
import com.nala.armoire.model.entity.ImageAsset;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between ImageAsset entity and DTOs
 */
@Component
public class ImageAssetMapper {
    public ImageAssetResponse toResponse(ImageAsset entity) {
        if (entity == null) {
            return null;
        }
        return ImageAssetResponse.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .imageUrl(entity.getImageUrl())
                .fileSize(entity.getFileSize())
                .mimeType(entity.getMimeType())
                .dimensions(entity.getDimensions())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public List<ImageAssetResponse> toResponseList(List<ImageAsset> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
