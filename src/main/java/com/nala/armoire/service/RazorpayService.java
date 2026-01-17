package com.nala.armoire.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Razorpay Payment Service
 * Handles payment creation and verification
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayService {

    private final RazorpayClient razorpayClient;

    @Value("${razorpay.currency}")
    private String currency;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    /**
     * Create Razorpay order
     * Amount should be in smallest currency unit (paise for INR)
     */
    public String createRazorpayOrder(BigDecimal amount, String receiptId) throws RazorpayException {
        try {
            // Convert to paise (multiply by 100)
            int amountInPaise = amount.multiply(BigDecimal.valueOf(100)).intValue();

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receiptId);
            orderRequest.put("payment_capture", 1); // Auto capture

            Order order = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            log.info("Razorpay order created: {}, amount: {}", razorpayOrderId, amount);
            return razorpayOrderId;

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order", e);
            throw e;
        }
    }

    /**
     * Verify payment signature
     * This ensures payment is authentic and not tampered
     */
    public boolean verifyPaymentSignature(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature) {

        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", razorpayOrderId);
            attributes.put("razorpay_payment_id", razorpayPaymentId);
            attributes.put("razorpay_signature", razorpaySignature);

            boolean isValid = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);

            if (isValid) {
                log.info("Payment signature verified successfully: {}", razorpayPaymentId);
            } else {
                log.warn("Invalid payment signature: {}", razorpayPaymentId);
            }

            return isValid;

        } catch (RazorpayException e) {
            log.error("Error verifying payment signature", e);
            return false;
        }
    }

    /**
     * Verify webhook signature
     * Used to verify Razorpay webhook events
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            return Utils.verifyWebhookSignature(payload, signature, razorpayKeySecret);
        } catch (RazorpayException e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }
}
