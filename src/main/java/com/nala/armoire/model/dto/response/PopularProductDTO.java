package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Popular Product DTO
 * Product popularity metrics for dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularProductDTO {

    private UUID id;
    private String name;
    private String slug;
    private Long reviewCount;
    private Double averageRating;
}
