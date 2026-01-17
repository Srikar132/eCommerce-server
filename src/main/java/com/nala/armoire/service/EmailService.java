package com.nala.armoire.service;


import com.nala.armoire.model.entity.User;
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

    public void sendVerificationEmail(User user) {
        String verificationLink = frontendUrl + "/verify-email?token=" + user.getVerificationToken();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Verify your Email - E-Commerce Store");
        message.setText(
                "Hello " + user.getUserName() + ",\n\n" +
                        "Thank you for registering. Please verify your email by clicking the link below: \n\n " + verificationLink + "\n\n" +
                        "This link will expire in 24 hours \n\n" +
                        "Best regards, \n" +
                        "E-commerce Team"
        );

        mailSender.send(message);

        System.out.println("Email sent in registeration\n");
    }

    public void sendPasswordResetEmail(User user, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Password Reset - E-Commerece Store");
        message.setText(
                "Hello" + user.getUserName() + ",\n\n" +
                        "You requested to reset your password. Click the link below to reset: \n\n" +
                        resetLink + "\n\n" +
                        "This link will expire in 1 hour.\n\n" +
                        "If you didn't request this, please ignore this email.\n\n" +
                        "Best regards,\n" +
                        "E-commerce Team"
        );

        mailSender.send(message);
    }


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
                        "E-commerce Team"
        );

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
                        "E-commerce Team"
        );

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
                        "E-commerce Team"
        );

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
                        "E-commerce Team"
        );

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
                        "Admin Dashboard: " + frontendUrl + "/admin/products"
        );

        mailSender.send(message);
    }

    
}
