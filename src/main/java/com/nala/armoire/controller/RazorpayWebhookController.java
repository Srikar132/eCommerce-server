package com.nala.armoire.controller;

import com.nala.armoire.model.entity.Order;
import com.nala.armoire.model.entity.PaymentStatus;
import com.nala.armoire.repository.OrderRepository;
import com.nala.armoire.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Razorpay Webhook Controller
 * Handles webhook events from Razorpay
 * Configure webhook URL in Razorpay Dashboard: https://your-domain.com/api/v1/webhooks/razorpay
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayService razorpayService;
    private final OrderRepository orderRepository;

    /**
     * POST /api/v1/webhooks/razorpay
     * Handle Razorpay webhook events
     */
    @PostMapping("/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {

        log.info("Received Razorpay webhook");

        // Verify webhook signature
        boolean isValid = razorpayService.verifyWebhookSignature(payload, signature);

        if (!isValid) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            JSONObject webhook = new JSONObject(payload);
            String event = webhook.getString("event");

            log.info("Processing Razorpay event: {}", event);

            switch (event) {
                case "payment.authorized":
                    handlePaymentAuthorized(webhook);
                    break;

                case "payment.captured":
                    handlePaymentCaptured(webhook);
                    break;

                case "payment.failed":
                    handlePaymentFailed(webhook);
                    break;

                case "order.paid":
                    handleOrderPaid(webhook);
                    break;

                default:
                    log.info("Unhandled event: {}", event);
            }

            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.internalServerError().body("Error processing webhook");
        }
    }

    private void handlePaymentAuthorized(JSONObject webhook) {
        try {
            JSONObject payload = webhook.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String razorpayOrderId = payload.getString("order_id");
            String razorpayPaymentId = payload.getString("id");

            Order order = orderRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);

            if (order != null) {
                order.setRazorpayPaymentId(razorpayPaymentId);
                order.setPaymentStatus(PaymentStatus.PROCESSING);
                orderRepository.save(order);

                log.info("Payment authorized for order: {}", order.getOrderNumber());
            }

        } catch (Exception e) {
            log.error("Error handling payment authorized event", e);
        }
    }

    private void handlePaymentCaptured(JSONObject webhook) {
        try {
            JSONObject payload = webhook.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String razorpayOrderId = payload.getString("order_id");

            Order order = orderRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);

            if (order != null) {
                order.setPaymentStatus(PaymentStatus.PAID);
                orderRepository.save(order);

                log.info("Payment captured for order: {}", order.getOrderNumber());
            }

        } catch (Exception e) {
            log.error("Error handling payment captured event", e);
        }
    }

    private void handlePaymentFailed(JSONObject webhook) {
        try {
            JSONObject payload = webhook.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String razorpayOrderId = payload.getString("order_id");

            Order order = orderRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);

            if (order != null) {
                order.setPaymentStatus(PaymentStatus.FAILED);
                orderRepository.save(order);

                log.info("Payment failed for order: {}", order.getOrderNumber());
            }

        } catch (Exception e) {
            log.error("Error handling payment failed event", e);
        }
    }

    private void handleOrderPaid(JSONObject webhook) {
        try {
            JSONObject payload = webhook.getJSONObject("payload")
                    .getJSONObject("order")
                    .getJSONObject("entity");

            String razorpayOrderId = payload.getString("id");

            Order order = orderRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);

            if (order != null) {
                order.setPaymentStatus(PaymentStatus.PAID);
                orderRepository.save(order);

                log.info("Order paid event for order: {}", order.getOrderNumber());
            }

        } catch (Exception e) {
            log.error("Error handling order paid event", e);
        }
    }
}