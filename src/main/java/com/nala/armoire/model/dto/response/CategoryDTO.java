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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // This excludes null fields from JSON
public class CategoryDTO {
    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private UUID parentId;
    private ParentCategoryInfo parent; // Full parent category information
    private Integer displayOrder;
    private List<CategoryDTO> subCategories;
    private String fullPath;
    private List<String> hierarchy; // Hierarchy path from root to current (e.g., ["Men", "Topwear", "T-Shirts"])
    
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