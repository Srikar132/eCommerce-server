package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Price range for slider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceRange {
    private BigDecimal min;
    private BigDecimal max;
}