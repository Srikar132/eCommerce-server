package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatsResponse {
    private long totalCategories;
    private long activeCategories;
    private long inactiveCategories;
    private long rootCategories;
    private long categoriesWithProducts;
    private long emptyCategoriesCount;
    private int maxDepth;
}
