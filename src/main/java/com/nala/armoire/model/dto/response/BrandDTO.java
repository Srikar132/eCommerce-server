package com.nala.armoire.model.dto.response;

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
public class BrandDTO {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String logoUrl;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
