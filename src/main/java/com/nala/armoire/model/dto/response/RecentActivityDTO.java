package com.nala.armoire.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Recent Activity DTO
 * Recent system activity for dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivityDTO {

    private Long recentOrders24h;
    private Long pendingOrders;
    private Long processingOrders;
}
