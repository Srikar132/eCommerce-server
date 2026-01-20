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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DesignDTO {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String name;
    private String slug;
    private String description;
    private String designImageUrl;
    private String thumbnailUrl;
    private List<String> tags;
    private Double designPrice;
    
    @JsonProperty("isActive")
    private Boolean isActive;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
