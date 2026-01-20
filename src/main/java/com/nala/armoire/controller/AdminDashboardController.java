package com.nala.armoire.controller;

import com.nala.armoire.model.dto.response.*;
import com.nala.armoire.model.entity.OrderStatus;
import com.nala.armoire.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin Dashboard Controller
 * Provides analytics and statistics for admin dashboard
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final DesignRepository designRepository;

    /**
     * GET /api/v1/admin/dashboard/stats
     * Get overall dashboard statistics
     * 
     * @return Dashboard statistics with counts and totals
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        log.info("Admin: Fetching dashboard statistics");

        // User statistics
        long totalUsers = userRepository.count();

        // Product statistics
        long totalProducts = productRepository.count();
        long activeProducts = productRepository.findAll().stream()
                .filter(p -> p.getIsActive())
                .count();

        // Order statistics
        long totalOrders = orderRepository.count();

        // Revenue statistics (confirmed and delivered orders)
        List<OrderStatus> paidStatuses = Arrays.asList(
                OrderStatus.CONFIRMED,
                OrderStatus.PROCESSING,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED
        );
        Double totalRevenue = orderRepository.getTotalRevenue(paidStatuses);

        // Recent activity (last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long recentOrders = orderRepository.findRecentOrdersByStatus(
                OrderStatus.CONFIRMED, sevenDaysAgo).size();

        // Design statistics
        long totalDesigns = designRepository.count();

        // Review statistics
        long totalReviews = reviewRepository.count();

        DashboardStatsDTO stats = DashboardStatsDTO.builder()
                .totalUsers(totalUsers)
                .totalProducts(totalProducts)
                .activeProducts(activeProducts)
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue != null ? totalRevenue : 0.0)
                .recentOrders(recentOrders)
                .totalDesigns(totalDesigns)
                .totalReviews(totalReviews)
                .build();

        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/v1/admin/dashboard/revenue
     * Get revenue data for charts
     * 
     * @param days Number of days to fetch (default: 30)
     * @return Revenue statistics with daily breakdown
     */
    @GetMapping("/revenue")
    public ResponseEntity<RevenueStatsDTO> getRevenueStats(
            @RequestParam(defaultValue = "30") Integer days) {

        log.info("Admin: Fetching revenue statistics for last {} days", days);

        List<OrderStatus> paidStatuses = Arrays.asList(
                OrderStatus.CONFIRMED,
                OrderStatus.PROCESSING,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED
        );

        // Generate daily revenue data
        List<RevenueStatsDTO.DailyRevenueDTO> dailyRevenue = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime dayStart = LocalDateTime.now().minusDays(i)
                    .withHour(0).withMinute(0).withSecond(0);

            // Note: This is a simplified implementation
            // In production, you'd want a custom query that filters by date range
            Double dayRevenue = 0.0; // Placeholder - implement date-range query

            RevenueStatsDTO.DailyRevenueDTO dailyData = RevenueStatsDTO.DailyRevenueDTO.builder()
                    .date(dayStart.toLocalDate().toString())
                    .revenue(dayRevenue)
                    .build();

            dailyRevenue.add(dailyData);
        }

        // Total revenue for period
        Double totalRevenue = orderRepository.getTotalRevenue(paidStatuses);

        RevenueStatsDTO revenueStats = RevenueStatsDTO.builder()
                .dailyRevenue(dailyRevenue)
                .totalRevenue(totalRevenue != null ? totalRevenue : 0.0)
                .periodDays(days)
                .build();

        return ResponseEntity.ok(revenueStats);
    }

    /**
     * GET /api/v1/admin/dashboard/popular-products
     * Get most popular products based on reviews
     * 
     * @param limit Maximum number of products to return (default: 10)
     * @return List of popular products with ratings
     */
    @GetMapping("/popular-products")
    public ResponseEntity<List<PopularProductDTO>> getPopularProducts(
            @RequestParam(defaultValue = "10") Integer limit) {

        log.info("Admin: Fetching top {} popular products", limit);

        List<PopularProductDTO> popularProducts = productRepository.findAll().stream()
                .limit(limit)
                .map(product -> {
                    Long reviewCount = reviewRepository.countByProductId(product.getId());
                    Double averageRating = reviewRepository.findAverageRatingByProductId(product.getId());

                    return PopularProductDTO.builder()
                            .id(product.getId())
                            .name(product.getName())
                            .slug(product.getSlug())
                            .reviewCount(reviewCount)
                            .averageRating(averageRating != null ? averageRating : 0.0)
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(popularProducts);
    }

    /**
     * GET /api/v1/admin/dashboard/recent-activity
     * Get recent system activity
     * 
     * @return Recent activity metrics
     */
    @GetMapping("/recent-activity")
    public ResponseEntity<RecentActivityDTO> getRecentActivity() {
        log.info("Admin: Fetching recent activity");

        // Recent orders (last 24 hours)
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        long recentOrders = orderRepository.findRecentOrdersByStatus(
                OrderStatus.CONFIRMED, twentyFourHoursAgo).size();

        // Orders requiring attention
        long pendingOrders = orderRepository.findByStatusOrderByCreatedAtDesc(
                OrderStatus.PENDING, Pageable.unpaged()).getTotalElements();

        long processingOrders = orderRepository.findByStatusOrderByCreatedAtDesc(
                OrderStatus.PROCESSING, Pageable.unpaged()).getTotalElements();

        RecentActivityDTO activity = RecentActivityDTO.builder()
                .recentOrders24h(recentOrders)
                .pendingOrders(pendingOrders)
                .processingOrders(processingOrders)
                .build();

        return ResponseEntity.ok(activity);
    }
}
