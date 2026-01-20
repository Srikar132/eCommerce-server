package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard Statistics DTO
 * Overall statistics for admin dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {

    // User statistics
    private Long totalUsers;

    // Product statistics
    private Long totalProducts;
    private Long activeProducts;

    // Order statistics
    private Long totalOrders;
    private Long recentOrders;

    // Revenue statistics
    private Double totalRevenue;

    // Design statistics
    private Long totalDesigns;

    // Review statistics
    private Long totalReviews;
}
