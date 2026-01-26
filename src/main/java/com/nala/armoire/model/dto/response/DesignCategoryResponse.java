package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Design Category
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignCategoryResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private Integer displayOrder;
    private Boolean isActive;
    private Integer designCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}