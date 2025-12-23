package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFacetsDTO {
    private List<FacetItem> categories;
    private List<FacetItem> brands;
    private List<FacetItem> sizes;
    private List<FacetItem> colors;
    private PriceRange priceRange;
    private Long totalProducts;
}