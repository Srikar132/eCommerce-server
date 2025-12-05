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
        String verificationLink = frontendUrl + "/verify-email?token" + user.getVerificationToken();

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
}
