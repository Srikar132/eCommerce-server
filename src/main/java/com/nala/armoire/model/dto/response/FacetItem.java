package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual facet item (category, brand, size, color)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacetItem {
    private String value;      // slug or value (e.g., "t-shirts", "M", "black")
    private String label;      // display name (e.g., "T-Shirts", "Medium", "Black")
    private Long count;        // number of products
    private Boolean selected;  // is this filter currently selected
    private String colorHex;   // for color facets only
}