package com.nala.armoire.model.dto.response;

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
public class DesignListDTO {
    private UUID id;
    private String name;
    private String slug;
    private String description;
    
    private String designImageUrl;      
    private String thumbnailUrl;
    
    // Design category info (e.g., "Animals", "Nature", "Abstract")
    private DesignCategoryDTO category;
    
    private List<String> tags;
    
    private Double designPrice;
    
    private Boolean isActive;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
