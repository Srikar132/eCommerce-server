package com.nala.armoire.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service to manage pricing configuration from application.properties
 * Centralizes GST rate, shipping cost, and shipping threshold configuration
 */
@Service
@Slf4j
@Getter
public class PricingConfigService {

    @Value("${gst.rate:18}")
    private int gstRatePercent;

    @Value("${shipping.cost.price:100}")
    private BigDecimal shippingCostAmount;

    @Value("${shipping.threshold:1000}")
    private BigDecimal shippingThreshold;

    /**
     * Get GST rate as decimal (e.g., 18% = 0.18)
     * @return GST rate as BigDecimal
     */
    public BigDecimal getGstRate() {
        BigDecimal rate = BigDecimal.valueOf(gstRatePercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        log.debug("GST Rate: {}% ({})", gstRatePercent, rate);
        return rate;
    }

    /**
     * Get shipping cost amount based on cart subtotal
     * Returns shipping cost if subtotal is below threshold, otherwise returns 0 (free shipping)
     * @param subtotal The cart subtotal amount
     * @return Shipping cost as BigDecimal (0 if free shipping applies)
     */
    public BigDecimal getShippingCost(BigDecimal subtotal) {
        if (subtotal == null) {
            subtotal = BigDecimal.ZERO;
        }
        
        // Free shipping if subtotal exceeds threshold
        if (subtotal.compareTo(shippingThreshold) >= 0) {
            log.debug("Free shipping applied - Subtotal: {} exceeds threshold: {}", subtotal, shippingThreshold);
            return BigDecimal.ZERO;
        }
        
        log.debug("Shipping cost applied: {} - Subtotal: {} below threshold: {}", 
                shippingCostAmount, subtotal, shippingThreshold);
        return shippingCostAmount;
    }

    /**
     * Get shipping threshold amount
     * @return Shipping threshold as BigDecimal
     */
    public BigDecimal getShippingThreshold() {
        return shippingThreshold;
    }

    /**
     * Get GST percentage for display purposes
     * @return GST rate as integer percentage
     */
    public int getGstRatePercent() {
        return gstRatePercent;
    }
}
