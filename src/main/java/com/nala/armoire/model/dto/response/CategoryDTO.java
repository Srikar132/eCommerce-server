package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Category Data Transfer Object with flexible field inclusion
 *
 * When minimal=true: Returns only id, name, slug
 * When minimal=false: Returns all fields (null fields are excluded from JSON)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Excludes null fields from JSON response
public class CategoryDTO {

    // === Always Included Fields (minimal=true or false) ===
    private UUID id;
    private String name;
    private String slug;

    // === Optional Fields (only when minimal=false) ===
    private String description;
    private String imageUrl;
    private UUID parentId;
    private ParentCategoryInfo parent;
    private Integer displayOrder;
    private List<CategoryDTO> subCategories;
    private String fullPath;
    private List<String> hierarchy;

    @JsonProperty("isActive")
    private Boolean isActive;

    private LocalDateTime createdAt;
    private Long productCount;

    /**
     * Simplified parent category information to avoid deep nesting
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ParentCategoryInfo {
        private UUID id;
        private String name;
        private String slug;
        private UUID parentId; // Grandparent ID if exists
    }
}