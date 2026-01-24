package com.nala.armoire.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void sendOrderConfirmationEmail(com.nala.armoire.model.entity.Order order) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(order.getUser().getEmail());
        message.setSubject("Order Confirmed - " + order.getOrderNumber());
        message.setText(
                "Hello " + order.getUser().getUserName() + ",\n\n" +
                        "Thank you for your order!\n\n" +
                        "Order Number: " + order.getOrderNumber() + "\n" +
                        "Total Amount: ₹" + order.getTotalAmount() + "\n" +
                        "Payment Status: " + order.getPaymentStatus() + "\n" +
                        "Estimated Delivery: " + order.getEstimatedDeliveryDate() + "\n\n" +
                        "You can track your order at: " + frontendUrl + "/orders/" + order.getOrderNumber() + "\n\n" +
                        "Best regards,\n" +
                        "E-commerce Team");

        mailSender.send(message);
    }

    public void sendOrderShippedEmail(com.nala.armoire.model.entity.Order order) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(order.getUser().getEmail());
        message.setSubject("Order Shipped - " + order.getOrderNumber());
        message.setText(
                "Hello " + order.getUser().getUserName() + ",\n\n" +
                        "Great news! Your order has been shipped.\n\n" +
                        "Order Number: " + order.getOrderNumber() + "\n" +
                        "Tracking Number: " + order.getTrackingNumber() + "\n" +
                        "Carrier: " + order.getCarrier() + "\n" +
                        "Estimated Delivery: " + order.getEstimatedDeliveryDate() + "\n\n" +
                        "Track your order: " + frontendUrl + "/orders/" + order.getOrderNumber() + "\n\n" +
                        "Best regards,\n" +
                        "E-commerce Team");

        mailSender.send(message);
    }

    public void sendOrderDeliveredEmail(com.nala.armoire.model.entity.Order order) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(order.getUser().getEmail());
        message.setSubject("Order Delivered - " + order.getOrderNumber());
        message.setText(
                "Hello " + order.getUser().getUserName() + ",\n\n" +
                        "Your order has been delivered successfully!\n\n" +
                        "Order Number: " + order.getOrderNumber() + "\n" +
                        "Delivered At: " + order.getDeliveredAt() + "\n\n" +
                        "We hope you enjoy your purchase. Please leave a review!\n\n" +
                        "Best regards,\n" +
                        "E-commerce Team");

        mailSender.send(message);
    }

    public void sendOrderCancellationEmail(com.nala.armoire.model.entity.Order order) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(order.getUser().getEmail());
        message.setSubject("Order Cancelled - " + order.getOrderNumber());
        message.setText(
                "Hello " + order.getUser().getUserName() + ",\n\n" +
                        "Your order has been cancelled as requested.\n\n" +
                        "Order Number: " + order.getOrderNumber() + "\n" +
                        "Refund will be processed within 5-7 business days.\n\n" +
                        "If you have any questions, please contact our support.\n\n" +
                        "Best regards,\n" +
                        "E-commerce Team");

        mailSender.send(message);
    }

    public void sendLowStockAlertEmail(String adminEmail, com.nala.armoire.model.entity.ProductVariant variant) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(adminEmail);
        message.setSubject("Low Stock Alert - " + variant.getProduct().getName());
        message.setText(
                "Low Stock Alert!\n\n" +
                        "Product: " + variant.getProduct().getName() + "\n" +
                        "Variant: " + variant.getSize() + " - " + variant.getColor() + "\n" +
                        "Current Stock: " + variant.getStockQuantity() + "\n\n" +
                        "Please restock this item.\n\n" +
                        "Admin Dashboard: " + frontendUrl + "/admin/products");

        mailSender.send(message);
    }

}
