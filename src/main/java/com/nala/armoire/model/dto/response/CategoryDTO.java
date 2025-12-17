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
public class CategoryDTO {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private UUID parentId;
    private Integer displayOrder;
    private List<CategoryDTO> subCategories;
    private String fullPath;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private Long productCount;

}
