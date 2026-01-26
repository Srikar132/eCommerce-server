package com.nala.armoire.mapper;

import com.nala.armoire.model.dto.request.DesignCategoryRequest;
import com.nala.armoire.model.dto.response.DesignCategoryResponse;
import com.nala.armoire.model.entity.DesignCategory;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between DesignCategory entity and DTOs
 */
@Component
public class DesignCategoryMapper {

    /**
     * Convert entity to response DTO
     */
    public DesignCategoryResponse toResponse(DesignCategory entity) {
        if (entity == null) {
            return null;
        }

        // Safely get design count without forcing collection initialization
        int designCount = 0;
        if (Hibernate.isInitialized(entity.getDesigns())) {
            designCount = entity.getDesigns() != null ? entity.getDesigns().size() : 0;
        }

        return DesignCategoryResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .slug(entity.getSlug())
                .description(entity.getDescription())
                .displayOrder(entity.getDisplayOrder())
                .isActive(entity.getIsActive())
                .designCount(designCount)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Convert list of entities to list of response DTOs
     */
    public List<DesignCategoryResponse> toResponseList(List<DesignCategory> entities) {
        if (entities == null) {
            return null;
        }

        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convert request DTO to entity (for creation)
     */
    public DesignCategory toEntity(DesignCategoryRequest request) {
        if (request == null) {
            return null;
        }

        return DesignCategory.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder())
                .isActive(true)  // Default to active for new categories
                .build();
    }

    /**
     * Update entity from request DTO (for updates)
     */
    public void updateEntityFromRequest(DesignCategory entity, DesignCategoryRequest request) {
        if (entity == null || request == null) {
            return;
        }

        entity.setName(request.getName());
        entity.setSlug(request.getSlug());
        entity.setDescription(request.getDescription());

        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }
    }
}