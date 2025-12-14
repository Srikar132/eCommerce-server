package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    private String imageUrl;
    private String thumbnailUrl;
    private List<String> tags;
    private List<String> allowedProductTypes;
    private Boolean isActive;
    private Boolean isPremium;
    private Long downloadCount;
    private LocalDateTime createdAt;
}
