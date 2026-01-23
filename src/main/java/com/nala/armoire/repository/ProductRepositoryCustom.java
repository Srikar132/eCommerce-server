package com.nala.armoire.repository;

import com.nala.armoire.model.dto.response.ProductFacetsDTO;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepositoryCustom {
    
    /**
     * Get product facets (categories, brands, sizes, colors, price range)
     * based on current filters
     */
    ProductFacetsDTO getProductFacets(
            List<String> categorySlugs,
            List<String> brandSlugs,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> sizes,
            List<String> colors,
            Boolean isCustomizable,
            String searchQuery
    );

    /**
     * Get product name autocomplete suggestions
     */
    List<String> findProductNameAutocomplete(String query, Integer limit);
}
