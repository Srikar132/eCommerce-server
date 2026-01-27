package com.nala.armoire.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Name can be at most 100 characters")
    private String name;

    @Size(max = 100, message = "Slug can be at most 100 characters")
    private String slug;

    @Size(max = 5000, message = "Description can be at most 5000 characters")
    private String description;

    @Size(max = 2048, message = "Image URL can be at most 2048 characters")
    private String imageUrl;

    private UUID parentId;

    @Min(value = 0, message = "Display order must be zero or positive")
    private Integer displayOrder;

    private Boolean isActive;
}
