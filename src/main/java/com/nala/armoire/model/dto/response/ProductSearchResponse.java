package com.nala.armoire.model.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Combined response with products and facets
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponse {
    private PagedResponse<ProductDTO> products;
    private ProductFacetsDTO facets;
}
