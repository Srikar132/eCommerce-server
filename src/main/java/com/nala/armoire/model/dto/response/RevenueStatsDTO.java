package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Revenue Statistics DTO
 * Revenue data for dashboard charts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueStatsDTO {

    private List<DailyRevenueDTO> dailyRevenue;
    private Double totalRevenue;
    private Integer periodDays;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenueDTO {
        private String date; // Format: YYYY-MM-DD
        private Double revenue;
    }
}
