package com.nala.armoire.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DesignCategoryDTO {
    private UUID id;
    private String name;
    private String slug;
    private String description;
    private Integer displayOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
